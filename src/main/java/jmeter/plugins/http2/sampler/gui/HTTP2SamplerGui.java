/*
 *  Copyright 2015 Ryo Okubo
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jmeter.plugins.http2.sampler.gui;

import jmeter.plugins.http2.sampler.HTTP2Sampler;
import org.apache.jmeter.gui.util.HorizontalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledChoice;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.awt.*;
import javax.swing.*;

public class HTTP2SamplerGui extends AbstractSamplerGui {

    private static final Logger log = LoggingManager.getLoggerForClass();

    private JLabeledChoice method;
    private JTextField scheme;
    private JTextField domain;
    private JTextField port;
    private JTextField path;

    public HTTP2SamplerGui(){
        super();

        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        this.add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel webRequestPanel = new JPanel();
        webRequestPanel.setLayout(new BorderLayout());

        webRequestPanel.add(getWebServerPanel(), BorderLayout.NORTH);
        webRequestPanel.add(getPathPanel(), BorderLayout.CENTER);

        this.add(webRequestPanel, BorderLayout.CENTER);
    }

    @Override
    public String getStaticLabel() {
        return "HTTP2 Sampler";
    }

    public String getLabelResource() {
        return "HTTP2 Sampler";
    }

    public TestElement createTestElement() {
        HTTP2Sampler sampler = new HTTP2Sampler();

        modifyTestElement(sampler);

        return sampler;
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        HTTP2Sampler sampler = (HTTP2Sampler)element;
        /* method.setText(sampler.getMethod()); */
        scheme.setText(sampler.getScheme());
        domain.setText(sampler.getDomain());
        port.setText(sampler.getPortAsString());
        path.setText(sampler.getPath());
    }

    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        /* element.setProperty(HTTP2Sampler.METHOD, method.getText()); */
        element.setProperty(HTTP2Sampler.METHOD, HTTP2Sampler.DEFAULT_METHOD);
        element.setProperty(HTTP2Sampler.SCHEME, scheme.getText());
        element.setProperty(HTTP2Sampler.DOMAIN, domain.getText());
        element.setProperty(HTTP2Sampler.PORT, port.getText());
        element.setProperty(HTTP2Sampler.PATH, path.getText());
    }

    private final JPanel getWebServerPanel() {
        JPanel webServerPanel = new HorizontalPanel();

        final JPanel schemePanel = getSchemePanel();
        final JPanel domainPanel = getDomainPanel();
        final JPanel portPanel = getPortPanel();

        webServerPanel.add(schemePanel, BorderLayout.WEST);
        webServerPanel.add(domainPanel, BorderLayout.CENTER);
        webServerPanel.add(portPanel, BorderLayout.EAST);

        return webServerPanel;
    }

    private final JPanel getSchemePanel() {
        scheme = new JTextField(10);

        JLabel label = new JLabel("Scheme");
        label.setLabelFor(scheme);

        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.add(label, BorderLayout.WEST);
        panel.add(scheme, BorderLayout.CENTER);

        return panel;
    }

    private final JPanel getDomainPanel() {
        domain = new JTextField(20);

        JLabel label = new JLabel("Domain");
        label.setLabelFor(domain);

        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.add(label, BorderLayout.WEST);
        panel.add(domain, BorderLayout.CENTER);

        return panel;
    }

    private final JPanel getPortPanel() {
        port = new JTextField(10);

        JLabel label = new JLabel("Port");
        label.setLabelFor(port);

        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.add(label, BorderLayout.WEST);
        panel.add(port, BorderLayout.CENTER);

        return panel;
    }

    private final JPanel getPathPanel() {
        path = new JTextField(15);

        JLabel label = new JLabel("Path");
        label.setLabelFor(path);

        JPanel pathPanel = new HorizontalPanel();
        pathPanel.add(label);
        pathPanel.add(path);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(pathPanel);

        return panel;
    }

}
