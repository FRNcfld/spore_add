# 本目录是本 mod 的 mixin 包（对应 spore_add.mixins.json 的 "package"）。
#
# 新增 mixin 类后，把它登记进 spore_add.mixins.json 的两个列表之一：
#   "mixins" —— 通用（两侧都应用）。目标是原版或第三方模组的普通类。
#   "client" —— 仅客户端。凡是在 mixin 里引用了客户端类（渲染、GuiGraphics、PoseStack 之类）
#               的，必须放这里，否则专用服务端加载到只存在于客户端的类会直接崩。
#
# 两处列表的具体成员请直接看那个 json —— 本文件刻意不列举类名，
# 免得每次加 mixin 都要回来同步一遍。漏登记的典型症状是"编译进 jar 但运行时不生效"，
# 不报错、只是静默失效，很难查。
