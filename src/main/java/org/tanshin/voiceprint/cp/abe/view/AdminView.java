package org.tanshin.voiceprint.cp.abe.view;

import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import io.ipfs.api.IPFS;
import io.ipfs.multihash.Multihash;
import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
import org.tanshin.voiceprint.cp.abe.repository.AuditLogRepository;
import org.tanshin.voiceprint.cp.abe.service.OfflinePoolManager;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Route(value = "admin", layout = MainView.class)
@UIScope
public class AdminView extends VerticalLayout {

    private final OfflinePoolManager poolManager;
    private final AuditLogRepository auditLogRepo;

    // UI 组件提升为成员变量以便刷新
    private final ProgressBar progressBar = new ProgressBar();
    private final Span poolStatusText = new Span();
    private final Grid<AuditLog> auditGrid = new Grid<>(AuditLog.class, false);

    public AdminView(OfflinePoolManager poolManager, AuditLogRepository auditLogRepo) {
        this.poolManager = poolManager;
        this.auditLogRepo = auditLogRepo;

        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // 1. 构建离线池监控模块
        setupOfflinePoolSection();

        add(new Hr()); // 分割线

        // 2. 构建安全审计日志模块 (增强了对 IPFS 重加密日志的解析展示)
        setupAuditLogSection();

        // 初始化加载数据
        refreshData();
    }

    private void setupOfflinePoolSection() {
        add(new H3("⚡ 离线预计算池监控 (IoT 终端加速层)"));

        Span desc = new Span("监控系统后台提前计算的 ABE 盲化因子，确保低算力终端能实现毫秒级加密。");
        desc.getStyle().set("color", "gray").set("font-size", "0.9em");
        add(desc);

        progressBar.setMin(0);
        progressBar.setMax(1000);
        progressBar.setWidth("400px");

        Button refreshBtn = new Button("刷新状态", new Icon(VaadinIcon.REFRESH), e -> refreshData());

        Button refillBtn = new Button("触发后台补充", e -> {
            poolManager.forceReplenishAll();
            Notification.show("🚀 离线池补充任务已在后台异步执行！请稍后点击刷新查看进度。", 3000, Notification.Position.TOP_CENTER);
            refreshData();
        });
        refillBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout controls = new HorizontalLayout(progressBar, poolStatusText);
        controls.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        add(controls, new HorizontalLayout(refreshBtn, refillBtn));
    }

    private void setupAuditLogSection() {
        // 头部标题与描述
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(Alignment.BASELINE);
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);

        H3 logTitle = new H3("🛡️ KGC 与终端行为审计中心 (不可篡改追踪)");
        Button globalRefreshBtn = new Button("刷新数据面板", new Icon(VaadinIcon.REFRESH), e -> refreshData());

        headerLayout.add(logTitle, globalRefreshBtn);
        add(headerLayout);

        Span desc = new Span("记录所有 ABE 密钥生成、密文存取、属性更新及终端越权解密尝试行为。防抵赖，可追溯。");
        desc.getStyle().set("color", "gray").set("font-size", "0.9em");
        add(desc);

        // --- 核心优化：表格列设计融入重加密与 IPFS 存证解析 ---

        // 1. 操作时间
        auditGrid.addColumn(log -> {
            if (log.getOpTime() == null) return "-";
            return log.getOpTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }).setHeader("操作时间").setSortable(true).setWidth("165px").setFlexGrow(0);

        // 2. 终端执行人身份
        auditGrid.addColumn(AuditLog::getOperatorName).setHeader("终端接入 / 操作者").setWidth("180px").setSortable(true);

        // 3. 系统行为分类 (提取干净的行为标签)
        auditGrid.addColumn(log -> {
            String rawAction = log.getActionType();
            if (rawAction != null && rawAction.contains("[")) {
                return rawAction.substring(0, rawAction.indexOf("[")).trim(); // 过滤掉后面的 [IPFS Log: ...]
            }
            return rawAction;
        }).setHeader("系统行为分类").setWidth("160px").setSortable(true);

        // 4. 数据密文 CID
        auditGrid.addColumn(AuditLog::getTargetCid).setHeader("数据密文 CID (IPFS哈希)").setWidth("280px");

        // ✨ 5. 新增：IPFS 凭证审计列 (智能检测并渲染日志提取按钮)
        auditGrid.addComponentColumn(log -> {
            String rawAction = log.getActionType();
            HorizontalLayout cellLayout = new HorizontalLayout();

            if (rawAction != null && rawAction.contains("IPFS Log:")) {
                try {
                    // 动态提取 CID 哈希值
                    int startIdx = rawAction.indexOf("IPFS Log:") + 9;
                    int endIdx = rawAction.indexOf("]");
                    String logCid = rawAction.substring(startIdx, endIdx).trim();

                    Button viewLogBtn = new Button("查看凭证", new Icon(VaadinIcon.FILE_SEARCH));
                    viewLogBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    viewLogBtn.addClickListener(e -> fetchAndShowIpfsLog(logCid));

                    Span logCidSpan = new Span(logCid.substring(0, 6) + "...");
                    logCidSpan.getStyle().set("font-family", "monospace").set("color", "var(--lumo-primary-text-color)");
                    logCidSpan.getElement().setAttribute("title", logCid); // 悬浮悬停看全称

                    cellLayout.add(logCidSpan, viewLogBtn);
                } catch (Exception ex) {
                    cellLayout.add(new Span("-"));
                }
            } else {
                cellLayout.add(new Span("本地落库"));
            }
            return cellLayout;
        }).setHeader("审计日志凭证 (分布式存储)").setWidth("180px").setFlexGrow(0);

        // 6. 安全校验状态
        auditGrid.addComponentColumn(log -> {
            boolean isSuccess = log.isSuccess();
            Span badge = new Span(isSuccess ? "授权/执行成功" : "越权拦截/失败");
            badge.getElement().getThemeList().add("badge " + (isSuccess ? "success primary" : "error primary"));
            return badge;
        }).setHeader("安全校验状态").setWidth("140px").setFlexGrow(0);

        auditGrid.setWidthFull();
        auditGrid.setHeight("500px"); // 提升了高度以承载更多审计事件

        add(auditGrid);
    }

    // ✨ 新增方法：穿透到本地 IPFS 节点读取并弹出防篡改 JSON 审计日志面板
    private void fetchAndShowIpfsLog(String cid) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("🔐 属性更新 - 分布式存证原文 (防抵赖凭证)");
        dialog.setWidth("550px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Pre jsonArea = new Pre();
        jsonArea.getStyle()
                .set("background-color", "#f5f5f5")
                .set("padding", "15px")
                .set("border-radius", "5px")
                .set("font-family", "monospace")
                .set("font-size", "0.85em")
                .set("overflow-x", "auto")
                .set("width", "100%");

        jsonArea.setText("正在连接 IPFS 节点读取存证数据...");
        layout.add(jsonArea);
        dialog.add(layout);

        Button closeBtn = new Button("关闭", e -> dialog.close());
        dialog.getFooter().add(closeBtn);
        dialog.open();

        // 异步读取 IPFS 防止阻塞主 UI 线程
        var ui = getUI().get();
        new Thread(() -> {
            try {
                IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
                byte[] rawLogBytes = ipfs.cat(Multihash.fromBase58(cid));
                String jsonStr = new String(rawLogBytes, StandardCharsets.UTF_8);

                ui.access(() -> jsonArea.setText(jsonStr));
            } catch (Exception ex) {
                ui.access(() -> {
                    jsonArea.setText("读取失败，请检查 IPFS 守护进程状态。\n错误信息: " + ex.getMessage());
                    jsonArea.getStyle().set("color", "red");
                });
            }
        }).start();
    }

    private void refreshData() {
        // --- 刷新离线池数据 ---
        int totalStored = poolManager.getTotalSize();
        int activePools = poolManager.getPoolCount();

        progressBar.setValue(totalStored);
        poolStatusText.setText(String.format("总计可用离线包: %d (覆盖 %d 种高频策略组合)", totalStored, activePools));

        if (totalStored < 20) {
            progressBar.getElement().getThemeList().add("error");
            poolStatusText.getStyle().set("color", "var(--lumo-error-text-color)");
        } else {
            progressBar.getElement().getThemeList().remove("error");
            poolStatusText.getStyle().remove("color");
        }

        // --- 刷新审计日志数据 ---
        auditGrid.setItems(auditLogRepo.findAllByOrderByOpTimeDesc());
    }
}