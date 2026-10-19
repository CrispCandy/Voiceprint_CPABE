package org.tanshin.voiceprint.cp.abe.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouterLink;

/**
 * 毕设系统主入口 - 采用标准 RouterLayout 模式
 */

@PageTitle("声纹特征细粒度访问控制系统")
public class MainView extends AppLayout {

    public MainView() {
        H1 title = new H1("Fine-grained Voiceprint Access Control System");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "var(--lumo-space-m)");

        Tabs tabs = new Tabs();
        // 使用 RouterLink 绑定路由，Vaadin 会自动销毁和创建视图，彻底解决 State Node 冲突
        tabs.add(createTab("系统概览", VaadinIcon.DASHBOARD, DashboardView.class));
        tabs.add(createTab("数据拥有者", VaadinIcon.UPLOAD, DataOwnerView.class));
        tabs.add(createTab("数据使用者", VaadinIcon.USER_CHECK, DataConsumerView.class));
        tabs.add(createTab("审计管理", VaadinIcon.SHIELD, AdminView.class));

        tabs.getStyle().set("margin", "auto");
        addToNavbar(title, tabs);
    }

    private Tab createTab(String text, VaadinIcon icon, Class<? extends Component> navigationTarget) {
        RouterLink link = new RouterLink(navigationTarget);
        link.add(icon.create(), new Span(text));
        return new Tab(link);
    }
}