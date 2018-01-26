package edu.berkeley.cs61b.plugin;

import com.intellij.debugger.engine.SuspendContext;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.ThreadReference;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import traceprinter.JDI2JSON;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.swing.JComponent;
import java.util.ArrayList;

public class JavaVisualizerManager implements XDebugSessionListener {
	private static final String CONTENT_ID = "61B.JavaVisualizerContent2";

	private XDebugSession session;
	private Content content;
	private JComponent component;
	private WebView webView;
	private boolean webViewReady;
	private JDI2JSON jdi2json;

	private String lastTrace = null;

	public JavaVisualizerManager(XDebugSession session) {
		this.session = session;
		this.content = null;
		this.jdi2json = new JDI2JSON();
	}

	public void attach() {
		session.addSessionListener(this);
	}

	private void initializeComponent() {
		final JFXPanel jfxPanel = new JFXPanel();
		Platform.setImplicitExit(false);
		Platform.runLater(() -> {
			webView = new WebView();
			webView.getEngine().setOnStatusChanged((WebEvent<String> e) -> {
				if (e != null && e.getData() != null && e.getData().equals("Ready")) {
					webViewReady = true;
					visualize();
				}
			});
			webView.getEngine().load(getClass().getResource("/web/visualizer.html").toExternalForm());
			jfxPanel.setScene(new Scene(webView));
		});
		component = jfxPanel;
	}

	private void initializeContent() {
		initializeComponent();
		RunnerLayoutUi ui = session.getUI();
		content = ui.createContent(
				CONTENT_ID,
				component,
				"Java Visualizer",
				IconLoader.getIcon("/icons/jv.png"),
				null);
		UIUtil.invokeLaterIfNeeded(() -> ui.addContent(content));
	}

	@Override
	public void sessionPaused() {
		if (content == null) {
			initializeContent();
		}

		try {
			SuspendContext sc = (SuspendContext) session.getSuspendContext();
			ThreadReference reference = sc.getThread().getThreadReference();

			ArrayList<JsonObject> objs = jdi2json.convertExecutionPoint(reference);
			if (objs.size() > 0) {
				JsonArrayBuilder arr = Json.createArrayBuilder();
				objs.forEach(arr::add);
				lastTrace = arr.build().toString();

				visualize();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void visualize() {
		if (lastTrace != null) {
			Platform.runLater(() -> {
				if (webView != null && webViewReady) {
					JSObject window = (JSObject) webView.getEngine().executeScript("window");
					window.call("visualize", lastTrace);
				}
			});
		}
	}
}