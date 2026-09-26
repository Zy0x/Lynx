#!/bin/bash

# Fixed path to dns.conf
DNS_CONF="$DIR/dns.conf"

# Debugging path
echo "DEBUG: dns.conf path=$DNS_CONF"

# Check if dns.conf exists
if [ ! -f "$DNS_CONF" ]; then
    log_msg "ERROR: dns.conf not found at $DNS_CONF"
    exit 1
fi

# Source the configuration file
. "$DNS_CONF"

# Help function for DNS commands
help_dns() {
    echo "Usage: Lxcore -dns [provider|help]"
    echo ""
    echo "Options:"
    echo "  provider   Set the DNS provider (e.g., google, cloudflare, opendns, etc.)."
    echo "  help       Show this help message."
    echo ""
    echo "Available DNS Providers:"
    echo "  google, cloudflare, cloudflaremalware, cloudflareadult, opendns, quad9,"
    echo "  yandex, adguard, norton, he, nextdns, opennic, cleanbrowsing, cleanbrowsingsecurity,"
    echo "  comodo, dnswatch"
    echo ""
    echo "Examples:"
    echo "  Lxcore -dns google          # Set Google DNS"
    echo "  Lxcore -dns cloudflare      # Set Cloudflare DNS"
    echo "  Lxcore -dns help            # Show this help message"
}

# Function to configure DNS settings
main_dns() {
    local provider="$1"

    if [ -z "$provider" ]; then
        help_dns
        return 1
    fi

    # Convert provider name to uppercase for matching with dns.conf variables
    dns_provider="$(echo "$provider" | tr '[:lower:]' '[:upper:]')_DNS_"
    DNS_IPV4_TCP=$(eval echo \${${dns_provider}IPV4_TCP})
    DNS_IPV4_UDP=$(eval echo \${${dns_provider}IPV4_UDP})
    DNS_IPV6_TCP=$(eval echo \${${dns_provider}IPV6_TCP})
    DNS_IPV6_UDP=$(eval echo \${${dns_provider}IPV6_UDP})

    # Validate DNS provider
    if [ -z "$DNS_IPV4_TCP" ] || [ -z "$DNS_IPV4_UDP" ]; then
        log_msg "ERROR: Invalid DNS provider '$provider'. Use 'Lxcore -dns help' for a list of providers."
        return 1
    fi

    log_msg "Configuring DNS for provider: $provider"

    # Clear existing DNS rules
    iptables -t nat -F OUTPUT >/dev/null 2>&1
    ip6tables -t nat -F OUTPUT >/dev/null 2>&1

    # IPv4 Settings
    iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination "$DNS_IPV4_TCP:53"
    iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination "$DNS_IPV4_UDP:53"
    iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination "$DNS_IPV4_TCP:53"
    iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination "$DNS_IPV4_UDP:53"

    # IPv6 Settings
    if [ -n "$DNS_IPV6_TCP" ]; then
        ip6tables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination "$DNS_IPV6_TCP"
        ip6tables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination "$DNS_IPV6_TCP"
    fi

    if [ -n "$DNS_IPV6_UDP" ]; then
        ip6tables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination "$DNS_IPV6_UDP"
        ip6tables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination "$DNS_IPV6_UDP"
    fi

    log_msg "DNS successfully configured for provider: $provider"
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '🌐 𝘿𝙉𝙎 𝙎𝙚𝙩 𝙩𝙤 $provider'" >/dev/null 2>&1
}