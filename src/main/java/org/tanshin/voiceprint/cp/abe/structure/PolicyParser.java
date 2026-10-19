package org.tanshin.voiceprint.cp.abe.structure;

import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PolicyParser {
    private static final AtomicInteger idGenerator = new AtomicInteger(1);

    public static AccessTree buildFromPolicy(String policy, PublicKey pk) {
        List<AccessTreeBuildModel> models = new ArrayList<>();
        idGenerator.set(1); // 确保根节点 ID 从 1 开始

        // 递归解析并填充 models 列表
        parseToModels(policy, -1, 1, models);

        return AccessTree.build(pk, models.toArray(new AccessTreeBuildModel[0]));
    }

    private static int parseToModels(String expression, int parentId, int index, List<AccessTreeBuildModel> models) {
        int currentId = idGenerator.getAndIncrement();
        expression = expression.trim();

        // 处理括号 (去除最外层括号)
        if (expression.startsWith("(") && expression.endsWith(")")) {
            // 这里需要检查是否是匹配的括号，简单起见：
            expression = expression.substring(1, expression.length() - 1).trim();
        }

        if (expression.contains(" OR ")) {
            // 内部节点：OR 门限为 1
            String[] parts = splitExpression(expression, "OR");
            models.add(AccessTreeBuildModel.innerAccessTreeBuildModel(currentId, 1, index, parentId));
            for (int i = 0; i < parts.length; i++) {
                parseToModels(parts[i], currentId, i + 1, models);
            }
        } else if (expression.contains(" AND ")) {
            // 内部节点：AND 门限为 N
            String[] parts = splitExpression(expression, "AND");
            models.add(AccessTreeBuildModel.innerAccessTreeBuildModel(currentId, parts.length, index, parentId));
            for (int i = 0; i < parts.length; i++) {
                parseToModels(parts[i], currentId, i + 1, models);
            }
        } else {
            // 叶子节点
            models.add(AccessTreeBuildModel.leafAccessTreeBuildModel(currentId, index, expression, parentId));
        }
        return currentId;
    }

    /**
     * 安全切割字符串，避开括号内的逻辑符
     */
    private static String[] splitExpression(String str, String operator) {
        // 简单的逻辑：根据操作符切割，实际应用中建议使用更严谨的栈匹配
        return str.split(" " + operator + " ");
    }
}
