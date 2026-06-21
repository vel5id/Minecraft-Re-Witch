FROM itzg/minecraft-server:java17

ENV EULA=TRUE \
    TYPE=FORGE \
    VERSION=1.20.1 \
    ONLINE_MODE=FALSE \
    REMOVE_OLD_MODS=TRUE

COPY mods/ /mods/
