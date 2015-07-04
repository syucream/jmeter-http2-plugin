# jmeter-http2-plugin

Jmeter HTTP/2 sampler

## Dependencies

* [Netty 5 and netty-tcnative](http://netty.io/)
* [hpack](https://github.com/twitter/hpack)

## Quickstart

1. Build Netty 5 (Alpha3+) and netty-tcnative for your platform

2. Copy HTTP2Sampler.jar, netty-all.jar, netty-tcnative.jar and hpack.jar to lib/ext of jmeter directory

  * If you use gzip encoding, you must prepare jzlib.jar too.

3. Run JMeter

4. Write your test scenario with HTTP2Sampler

## License

Apache License 2.0
