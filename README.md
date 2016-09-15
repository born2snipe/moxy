# moxy

Is a simple point-to-point embeddable proxy server.

Warning: Does not support SSL

### Example Usage
    MoxyServer moxy = new MoxyServer();
    moxy.listenOn(9999).andConnectTo("localhost", 9876);
    moxy.start();
