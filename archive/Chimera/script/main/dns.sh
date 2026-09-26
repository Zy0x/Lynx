# DNS Changer
DNS_CloudflareXGoogle() {
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 8.8.4.4:53
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 8.8.4.4:53
    ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2606:4700:4700::1111
    ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 2001:4860:4860::8844
    ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2606:4700:4700::1111
    ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 2001:4860:4860::8844
}

DNS_Google() {
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 8.8.4.4:53
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 8.8.4.4:53
    ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2001:4860:4860::8888
    ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 2001:4860:4860::8844
    ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2001:4860:4860::8888
    ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 2001:4860:4860::8844
}

DNS_Cloudflare() {
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 1.0.0.1:53
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 1.0.0.1:53
    ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2606:4700:4700::1111
    ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 2606:4700:4700::1001
    ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2606:4700:4700::1111
    ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 2606:4700:4700::1001
}

DNS_AdGuard() {
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 94.140.14.14:53
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 94.140.15.15:53
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 94.140.14.14:53
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 94.140.15.15:53
    ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2a10:50c0::ad1:ff
    ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 2a10:50c0::ad2:ff
    ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2a10:50c0::ad1:ff
    ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 2a10:50c0::ad2:ff
}

DNS_OpenDNS() {
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 208.67.222.222:53
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 208.67.220.220:53
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 208.67.222.222:53
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 208.67.220.220:53
    ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2620:0:ccc::2:53
    ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 2620:0:ccd::2:53
    ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2620:0:ccc::2:53
    ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 2620:0:ccd::2:53
}

DNS_Quad9() {
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 9.9.9.9:53
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 9.9.9.9:53
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 9.9.9.9:53
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 9.9.9.9:53
    ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2620:fe::fe:53
    ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 2620:fe::fe:53
    ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 2620:fe::fe:53
    ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 2620:fe::fe:53
}