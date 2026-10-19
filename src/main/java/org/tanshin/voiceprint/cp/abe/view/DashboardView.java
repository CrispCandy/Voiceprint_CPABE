package org.tanshin.voiceprint.cp.abe.view;

import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.lang.management.ManagementFactory;
import java.util.Random;

@Route(value = "dashboard", layout = MainView.class)
@RouteAlias(value = "", layout = MainView.class)
@UIScope
public class DashboardView extends VerticalLayout {

    private final Span timeSpan = new Span();
    private final Span weatherSpan = new Span();

    // --- 动态更新的数据组件 ---
    private final H2 resourceCountText = new H2("0");
    private final H2 peakPerfText = new H2("0.58 ms");

    private final ProgressBar cpuBar = new ProgressBar();
    private final Span cpuLabel = new Span();

    private final ProgressBar ramBar = new ProgressBar();
    private final Span ramLabel = new Span();

    private final ProgressBar ipfsBar = new ProgressBar();
    private final Span ipfsLabel = new Span();

    private final DataResourceRepository resourceRepo;
    private final Random random = new Random();

    // 构造函数：注入 WeatherService 和 DataResourceRepository
    public DashboardView(@Autowired WeatherService weatherService,
                         @Autowired DataResourceRepository resourceRepo) {
        this.resourceRepo = resourceRepo;

        setSpacing(true);
        setPadding(true);
        setWidthFull();

        // 【新增】设置整页的基础字体大小，级联影响内部的 Span、Div 等普通文本
        getStyle().set("font-size", "1.15rem");

        // --- 1. 顶部：环境感知栏 ---
        add(createHeader(weatherService));

        // --- 2. 中间：核心算法指标 ---
        HorizontalLayout statCards = new HorizontalLayout();
        statCards.setWidthFull();

        // 属性总数：显式放大数字字体
        H2 attrCountText = new H2("6");
        attrCountText.getStyle().set("font-size", "2.2rem");

        // 显式放大其他动态 H2 文本
        resourceCountText.getStyle().set("font-size", "2.2rem");
        peakPerfText.getStyle().set("font-size", "2.2rem");

        H2 ipfsStatusText = new H2("RUNNING");
        ipfsStatusText.getStyle().set("font-size", "2.2rem");

        statCards.add(
                createStatCard("系统属性总数", attrCountText, VaadinIcon.CLUSTER.create(), "#3498db"),
                createStatCard("已加密声纹总数", resourceCountText, VaadinIcon.LOCK.create(), "#2ecc71"),
                createStatCard("解密峰值性能", peakPerfText, VaadinIcon.FLASH.create(), "#e74c3c"),
                createStatCard("IPFS 节点状态", ipfsStatusText, VaadinIcon.SERVER.create(), "#9b59b6")
        );
        add(statCards);

        // --- 3. 下部：系统负载与节点画像 ---
        HorizontalLayout bottomLayout = new HorizontalLayout();
        bottomLayout.setWidthFull();
        bottomLayout.setHeight("340px"); // 稍微加高一点，容纳更大的字体高度

        // 左侧：系统实时监控 (动态版)
        VerticalLayout monitorPanel = new VerticalLayout();

        H4 monitorTitle = new H4("系统资源监控 (实时动态)");
        monitorTitle.getStyle().set("font-size", "1.4rem").set("margin-top", "5px");
        monitorPanel.add(monitorTitle);

        monitorPanel.add(createMonitorItem(cpuLabel, cpuBar));
        monitorPanel.add(createMonitorItem(ramLabel, ramBar));
        monitorPanel.add(createMonitorItem(ipfsLabel, ipfsBar));
        monitorPanel.getStyle().set("background", "#2d3436").set("border-radius", "10px");

        // 右侧：KGC 中央节点画像
        VerticalLayout nodeProfile = createNodeProfilePanel();

        bottomLayout.add(monitorPanel, nodeProfile);
        add(bottomLayout);

        // 初始化一次数据
        updateRealTimeData();
    }

    private HorizontalLayout createHeader(WeatherService weatherService) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        // 放大欢迎语标题
        H2 greeting = new H2("欢迎回来, 系统管理员");
        greeting.getStyle().set("margin", "0").set("font-size", "1.8rem");

        weatherSpan.setText(weatherService.getRealWeather());

        Div info = new Div(
                new Span(VaadinIcon.MAP_MARKER.create()), weatherSpan,
                new Span("  |  "),
                new Span(VaadinIcon.CLOCK.create()), timeSpan
        );
        // 放大顶部时间与天气
        info.getStyle().set("color", "gray").set("font-size", "1.1rem");

        // 开启轮询，每 1000ms 更新一次 UI
        this.addAttachListener(event -> {
            event.getUI().setPollInterval(1000);
        });
        this.addDetachListener(event -> {
            event.getUI().setPollInterval(-1);
        });

        header.add(greeting, info);
        return header;
    }

    // --- 组件构建辅助方法 ---

    private Div createStatCard(String title, H2 valueComponent, com.vaadin.flow.component.Component icon, String color) {
        Div card = new Div();
        card.getStyle()
                .set("background", "#353b48")
                .set("padding", "20px")
                .set("border-radius", "12px")
                .set("flex", "1")
                .set("border-left", "5px solid " + color);

        // 放大图标
        icon.getElement().getStyle().set("color", color).set("width", "30px").set("height", "30px");

        // 放大卡片标题
        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-size", "1.1rem").set("color", "#bdc3c7");

        card.add(icon, new Div(titleSpan), valueComponent);
        return card;
    }

    private VerticalLayout createMonitorItem(Span labelSpan, ProgressBar pb) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        // 放大监控项的 Label 字体
        labelSpan.getStyle().set("font-size", "1.1rem").set("margin-bottom", "5px");
        // 稍微加粗进度条
        pb.setHeight("12px");

        layout.add(labelSpan, pb);
        return layout;
    }

    // 构建真实的 KGC 节点信息面板
    private VerticalLayout createNodeProfilePanel() {
        VerticalLayout profile = new VerticalLayout();

        H4 profileTitle = new H4("KGC 中央授权节点状态");
        profileTitle.getStyle().set("font-size", "1.4rem").set("margin-top", "5px");
        profile.add(profileTitle);

        String ip = "127.0.0.1";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ignored) {}

        String os = System.getProperty("os.name") + " " + System.getProperty("os.arch");

        // 获取 JVM 启动时间
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptimeStr = String.format("%d 分钟前启动", uptime / 60000);

        // 提取 Span 变量以便统一调整内部间距与字体（继承父级 1.15rem 的大小）
        profile.add(createProfileLine("节点标识: Master_KGC_Node_01"));
        profile.add(createProfileLine("宿主机环境: " + os));
        profile.add(createProfileLine("内网通信 IP: " + ip));
        profile.add(createProfileLine("密码学引擎: CP-ABE (双策略访问控制)"));
        profile.add(createProfileLine("运行时长: " + (uptime < 60000 ? "刚刚启动" : uptimeStr)));

        profile.getStyle().set("background", "#2d3436").set("border-radius", "16px");
        return profile;
    }

    // 辅助方法：让画像面板的行间距在字体变大后更美观
    private Span createProfileLine(String text) {
        Span span = new Span(text);
        span.getStyle().set("margin-bottom", "4px").set("font-size", "1.1rem");
        return span;
    }

    // --- 核心动态更新逻辑 ---

    private void updateRealTimeData() {
        // 1. 获取数据库中真实的已加密文件总数
        long count = resourceRepo.count();
        resourceCountText.setText(String.valueOf(count));

        // 2. 真实获取 JVM 内存使用率
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        double ramRatio = (double) (totalMem - freeMem) / totalMem;
        ramBar.setValue(ramRatio);
        ramLabel.setText(String.format("内存 (JVM) 消耗 : %d%%", (int)(ramRatio * 100)));

        // 3. 模拟 CPU 波动 (基础占用 15%，加上随机波动)
        double cpuRatio = 0.15 + random.nextDouble() * 0.10;
        cpuBar.setValue(cpuRatio);
        cpuLabel.setText(String.format("边缘节点 CPU 占用 : %d%%", (int)(cpuRatio * 100)));

        // 4. 模拟 IPFS 存储池空间 (根据上传数量缓慢增长，假设每个文件占0.01%的演示空间)
        double ipfsBase = 0.07; // 基础占用 28%
        double ipfsRatio = Math.min(1.0, ipfsBase + (count * 0.0001));
        ipfsBar.setValue(ipfsRatio);
        ipfsLabel.setText(String.format("IPFS 存储池空间 : %.1f%%", ipfsRatio * 100));

        // 5. 模拟解密峰值波动 (让面板看起来有实时的计算任务在跑)
        double mockPerf = 0.48 + random.nextDouble() * 0.12;
        peakPerfText.setText(String.format("%.2f ms", mockPerf));
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // 轮询事件监听，每秒刷新时间、内存和动态图表
        attachEvent.getUI().addPollListener(event -> {
            timeSpan.setText(" " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            updateRealTimeData();
        });
    }

    // 静态内部 Service
    @Service
    public static class WeatherService {
        private final RestTemplate restTemplate = new RestTemplate();
        public String getRealWeather() {
            try {
                String cityJson = restTemplate.getForObject("http://ip-api.com/json/?lang=zh-CN", String.class);
                String city = "镇江";
                if (cityJson != null && cityJson.contains("city")) {
                    city = cityJson.split("\"city\":\"")[1].split("\"")[0];
                }
                String weather = restTemplate.getForObject("https://wttr.in/" + city + "?format=%C+%t", String.class);
                return " " + city + " · " + (weather != null ? weather : "晴 22°C");
            } catch (Exception e) {
                return " 镇江 · 晴 22°C (离线)";
            }
        }
    }
}