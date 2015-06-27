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

import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.apache.jmeter.samplers.SampleResult;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.util.CharsetUtil;

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

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class NettyHttp2Client {
    private final String method;
    private final String host;
    private final int port;
    private final String path;

    private Bootstrap b;

    public NettyHttp2Client(String method, String host, int port, String path) {
        this.method = method;
        this.host = host;
        this.port = port;
        this.path = path;
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
        int streamId = 3;
        URI hostName = URI.create("https://" + host + ':' + port);

        FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, path);
        request.headers().addObject(HttpHeaderNames.HOST, hostName);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
        channel.writeAndFlush(request);
        responseHandler.put(streamId, channel.newPromise());

        try {
            responseHandler.awaitResponses(5, TimeUnit.SECONDS);
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
}
