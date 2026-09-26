# Dex2oat opt function
dex2oat_opt_enable()
{
sed -Ei "s/^description=\[.*\]/description=[ ⛔ Dᴇx2ᴏᴀᴛ Oᴘᴛɪᴍɪᴢᴇʀ ɪꜱ ʀᴜɴɴɪɴɢ... ]/" "$MODPROP"
su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera' 'Chimera' '⛔ Dᴇx2ᴏᴀᴛ Oᴘᴛɪᴍɪᴢᴇʀ ɪꜱ ʀᴜɴɴɪɴɢ...'" >/dev/null 2>&1
dexoat_opt
}