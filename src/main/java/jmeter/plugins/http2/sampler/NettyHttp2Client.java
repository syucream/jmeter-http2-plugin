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
package jmeter.plugins.http2.sampler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AsciiString;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;

import javax.net.ssl.SSLException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyHttp2Client {
    private final String method;
    private final String host;
    private final int port;
    private final String path;
    private final HeaderManager headerManager;

    private Bootstrap b;

    public NettyHttp2Client(String method, String host, int port, String path, HeaderManager headerManager) {
        this.method = method;
        this.host = host;
        this.port = port;
        this.path = path;
        this.headerManager = headerManager;
    }

    public SampleResult request() {
        SampleResult sampleResult = new SampleResult();

        final SslContext sslCtx = getSslContext();
        if (sslCtx == null) {
            sampleResult.setSuccessful(false);
            return sampleResult;
        }

        // Configure the client.
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE);
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.remoteAddress(host, port);
        b.handler(initializer);

        // Start sampling
        sampleResult.sampleStart();

        // Start the client.
        Channel channel = b.connect().syncUninterruptibly().channel();

        // Wait for the HTTP/2 upgrade to occur.
        Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
        try {
            http2SettingsHandler.awaitSettings(5, TimeUnit.SECONDS);
        } catch(Exception exception) {
            sampleResult.setSuccessful(false);
            return sampleResult;
        }

        HttpResponseHandler responseHandler = initializer.responseHandler();
        final int streamId = 3;
        final URI hostName = URI.create("https://" + host + ':' + port);

        // Set attributes to SampleResult
        try {
            sampleResult.setURL(new URL(hostName.toString()));
        } catch (MalformedURLException exception) {
            sampleResult.setSuccessful(false);
            return sampleResult;
        }

        FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, path);
        request.headers().addObject(HttpHeaderNames.HOST, hostName);

        // Add request headers set by HeaderManager
        if (headerManager != null) {
            CollectionProperty headers = headerManager.getHeaders();
            if (headers != null) {
                PropertyIterator i = headers.iterator();
                while (i.hasNext()) {
                    org.apache.jmeter.protocol.http.control.Header header
                        = (org.apache.jmeter.protocol.http.control.Header) i.next().getObjectValue();
                    request.headers().add(header.getName(), header.getValue());
                }
            }
        }

        channel.writeAndFlush(request);
        responseHandler.put(streamId, channel.newPromise());

        final SortedMap<Integer, FullHttpResponse> responseMap;
        try {
            responseMap = responseHandler.awaitResponses(5, TimeUnit.SECONDS);

            // Currently pick up only one response of a stream
            final FullHttpResponse response = responseMap.get(streamId);
            final AsciiString responseCode = response.status().codeAsText();
            final AsciiString reasonPhrase = response.status().reasonPhrase();
            sampleResult.setResponseCode(new StringBuilder(responseCode.length()).append(responseCode).toString());
            sampleResult.setResponseMessage(new StringBuilder(reasonPhrase.length()).append(reasonPhrase).toString());
            sampleResult.setResponseHeaders(getResponseHeaders(response));
        } catch(Exception exception) {
            sampleResult.setSuccessful(false);
            return sampleResult;
        }

        // Wait until the connection is closed.
        channel.close().syncUninterruptibly();

        // End sampling
        sampleResult.sampleEnd();
        sampleResult.setSuccessful(true);

        return sampleResult;
    }

    private SslContext getSslContext() {
        SslContext sslCtx = null;

        final SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;

        try {
            sslCtx = SslContextBuilder.forClient()
                .sslProvider(provider)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                    Protocol.ALPN,
                    SelectorFailureBehavior.NO_ADVERTISE,
                    SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2))
                .build();
        } catch(SSLException exception) {
            return null;
        }

        return sslCtx;
    }

    /**
     * Convert Response headers set by Netty stack to one String instance
     */
    private String getResponseHeaders(FullHttpResponse response) {
        StringBuilder headerBuf = new StringBuilder();

        Iterator<Entry<String, String>> iterator = response.headers().iteratorAsString();
        while(iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            headerBuf.append(entry.getKey());
            headerBuf.append(": ");
            headerBuf.append(entry.getValue());
            headerBuf.append("\n");
        }

        return headerBuf.toString();
    }
}
