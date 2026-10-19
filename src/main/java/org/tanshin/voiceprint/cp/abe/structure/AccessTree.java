package org.tanshin.voiceprint.cp.abe.structure;


import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @BelongsProject: JPBC-ABE
 * @BelongsPackage: com.duwei.jpbc.cp.abe.structure
 * @Author: duwei
 * @Date: 2022/7/21 17:07
 * @Description: 访问树结构
 */
@Data
public class AccessTree {
    private AccessTreeNode root;

    private AccessTree(AccessTreeNode root) {
        this.root = root;
    }

    /**
     * 构建访问树节点
     *
     * @return
     */
    public static AccessTree build(PublicKey publicKey) {
        AccessTreeNode root = new InnerAccessTreeNode(2, 1, null);
        List<AccessTreeNode> children = new ArrayList<>();
        children.add(new LeafAccessTreeNode("学生", publicKey, root, 1));
        children.add(new LeafAccessTreeNode("老师", publicKey, root, 2));
        children.add(new LeafAccessTreeNode("硕士", publicKey, root, 3));
        root.setChildren(children);
        return new AccessTree(root);
    }


    public static AccessTree build(PublicKey publicKey, AccessTreeBuildModel[] accessTreeBuildModels) {
        Map<Integer, AccessTreeNode> idNodeMap = new HashMap<>();
        for (int i = 0; i < accessTreeBuildModels.length; i++) {
            AccessTreeBuildModel model = accessTreeBuildModels[i];
            AccessTreeNode node = null;
            if (model.getType() == AccessTreeNodeType.INNER_NODE) {
                //内部节点
                node = new InnerAccessTreeNode(model.getThreshold(), model.getIndex());
            } else if (model.getType() == AccessTreeNodeType.LEAF_NODE) {
                //叶子节点
                node = new LeafAccessTreeNode(model.getAttribute(), publicKey, model.getIndex());
            }
            node.setParentId(model.getParentId());
            idNodeMap.put(model.getId(), node);
        }
        //父亲节点ID  -  集合
        Map<Integer, List<AccessTreeNode>> collect = idNodeMap.values().stream().collect(Collectors.groupingBy(node -> node.getParentId()));
        idNodeMap.forEach((id, node) -> {
            List<AccessTreeNode> accessTreeNodes = collect.get(id);
            if (accessTreeNodes != null) {
                node.setChildren(accessTreeNodes);
                accessTreeNodes.forEach((child) -> child.setParent(node));
            }
        });

        //根节点元素索引必须为1
        return new AccessTree(idNodeMap.get(1));
    }
    public static AccessTree buildFromPolicyString(PublicKey publicKey, String policy) {
        List<AccessTreeBuildModel> models = new ArrayList<>();
        AtomicInteger idGenerator = new AtomicInteger(1); // 从 ID=1 开始，符合你的根节点要求

        // 调用递归解析器
        parseToModels(policy, -1, 1, idGenerator, models);

        // 调用你现有的 build(PublicKey, AccessTreeBuildModel[]) 方法
        return AccessTree.build(publicKey, models.toArray(new AccessTreeBuildModel[0]));
    }

    private static int parseToModels(String expression, int parentId, int index,
                                     AtomicInteger idGen, List<AccessTreeBuildModel> models) {
        expression = expression.trim();
        int currentId = idGen.getAndIncrement();

        // 1. 处理括号：如果是 (A AND B) 这种，去掉外层括号
        if (expression.startsWith("(") && expression.endsWith(")")) {
            expression = expression.substring(1, expression.length() - 1).trim();
        }

        // 2. 识别逻辑操作符 (优先级: OR 低于 AND)
        if (expression.contains(" OR ")) {
            String[] parts = expression.split(" OR ");
            // 内部节点：OR 的门限(threshold)是 1
            models.add(AccessTreeBuildModel.innerAccessTreeBuildModel(currentId, 1, index, parentId));
            for (int i = 0; i < parts.length; i++) {
                parseToModels(parts[i], currentId, i + 1, idGen, models);
            }
        } else if (expression.contains(" AND ")) {
            String[] parts = expression.split(" AND ");
            // 内部节点：AND 的门限(threshold)是 N (全选)
            models.add(AccessTreeBuildModel.innerAccessTreeBuildModel(currentId, parts.length, index, parentId));
            for (int i = 0; i < parts.length; i++) {
                parseToModels(parts[i], currentId, i + 1, idGen, models);
            }
        } else {
            // 3. 叶子节点：即具体的属性字符串
            models.add(AccessTreeBuildModel.leafAccessTreeBuildModel(currentId, index, expression, parentId));
        }
        return currentId;
    }
}
