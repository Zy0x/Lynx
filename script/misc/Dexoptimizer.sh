#!/bin/bash

# Animator
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
NC='\033[0m' # No Color
spinner=("🌕" "🌖" "🌗" "🌘" "🌑" "🌒" "🌓" "🌔")
show_spinner() {
    local pid=$1
    echo -e "\033[1;31mThis process might take 15-20 minutes!\033[0m"
    echo ""
    echo -e "${CYAN}Optimizing Background Dexopt Jobs...${NC}"
    while kill -0 $pid 2>/dev/null; do
        for i in "${spinner[@]}"; do
            echo -ne "${YELLOW}\r$i${NC} "
            sleep 0.1
        done
    done
    echo -ne "\r"
    echo -e "${GREEN}Background Dexopt Job Optimization Completed!${NC}"
}

# Background DexOpt Optimization
pm bg-dexopt-job &
JOB_PID=$!
show_spinner $JOB_PID

# Compile Speed Profile
echo ""
echo -e "${CYAN}Compiling All Apps!${NC}"
pm compile -a -f -m speed-profile
echo -e "${GREEN}Done!${NC}"

# Compile Layout Rendering
echo ""
echo -e "${CYAN}Compiling All Apps Layout...${NC}"
pm compile -a -f --compile-layouts
echo -e "${GREEN}Done!${NC}"

# Exit
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '✅ DexOptimized Completed'" >/dev/null 2>&1
echo ""
echo -e "${RED}Press Enter to exit...${NC}"
read
