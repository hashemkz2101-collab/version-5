package hev.htproxy

object TProxyService {
    external fun TProxyStartService(config_path: String, fd: Int): Boolean
    external fun TProxyStopService(): Boolean
    external fun TProxyIsRunning(): Boolean
    external fun TProxyGetStats(): LongArray
    init { System.loadLibrary("hev-socks5-tunnel") }
}
