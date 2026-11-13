# 邮箱账号与云端备份：启用步骤

软件已包含真实 Supabase Auth 与备份接口客户端。当前 `app/src/main/assets/cloud/config.json` 的项目地址和公开密钥为空，因此注册／登录按钮尚未启用，不会发送邮箱、密码或计划。不能把这个安装包称为已上线的账号版。

## 云端准备

1. 在 https://supabase.com/dashboard 登录，由项目所有者创建项目。平台注册、条款接受、数据库密码和任何付费操作由所有者本人完成。不要把平台登录密码、数据库密码、secret/service_role 密钥贴进聊天或放进 APK。
2. 在新项目的 SQL Editor 执行 `migrations/001_accounts_and_backups.sql`。这是一次性迁移；后续改表新增迁移，不重复执行建表语句。
3. 在 Authentication 的 Email provider 中启用邮箱密码注册，保留邮箱验证。将最低密码长度设为至少 8。不要关闭验证来绕过邮件服务准备。
4. 配置邮件服务。Supabase 默认试用邮件通常只面向项目团队邮箱，且有发送限制；面向其他使用者时需配置自己的 SMTP。SMTP 登录信息仅保存在 Supabase 官方控制台。
5. 修改 Confirm signup 与 Reset password 邮件模板，显示 `{{ .Token }}`。本客户端输入邮箱验证码，不接收浏览器回跳中的访问令牌，也无需配置不安全的深链接。示例见下方。
6. 取得项目的 HTTPS Project URL 与 Publishable key（或旧版 anon key）。这两项属于移动端公开连接配置；不是项目管理权限。使用 `tools/configure_cloud.py` 从标准输入写入配置，或由维护者填入 `app/src/main/assets/cloud/config.json`，然后重新构建 APK。
7. 在测试项目执行 `tests/account_isolation.sql`，验证仅能读取自己的备份、匿名访问失败、直接写入被禁止、服务器校验摘要并只保留 7 份。脚本最终回滚。不要把未执行过的 SQL 测试称为通过。
8. 用两个测试账号和两台设备完成邮箱注册／验证、登录／找回密码、上传备份、跨账号隔离、换机下载及恢复预览。完成后才认定账号服务已启用。软件不会替用户创建测试邮箱或发送验证邮件，测试收件地址应由所有者指定。

## 验证邮件模板

主题：验证你的自律空间账号

```html
<h2>验证自律空间邮箱</h2>
<p>请回到自律空间，在“验证邮箱”页面输入：</p>
<p style="font-size:28px;letter-spacing:4px">{{ .Token }}</p>
<p>验证码只用于你主动发起的邮箱验证。如非本人操作，请忽略此邮件。</p>
```

主题：重设自律空间密码

```html
<h2>重设自律空间密码</h2>
<p>请回到自律空间的“找回密码”页面输入：</p>
<p style="font-size:28px;letter-spacing:4px">{{ .Token }}</p>
<p>然后在软件中设置新密码。如非本人操作，请忽略此邮件。</p>
```

## 行为与数据范围

- 邮箱与密码通过 HTTPS 发送到指定 Supabase 项目的 Auth 服务。密码不会写入本机文件、备份或日志。
- 登录会话使用 Android Keystore 的 AES-GCM 加密，存放在 noBackupFilesDir；不进入计划文件与便携备份。
- 计划仍默认保存在本机。每次手动云备份都会展示当前账号与内容摘要，经确认才上传快照；没有自动跨设备合并。
- 服务端通过已验证 JWT 确定账号，客户端不能指定备份所有者。读取由 RLS 限制；所有写入仅由受控函数执行，服务端保留最近 7 份。
- 云备份含计划、作息、课程文字与阅读进度；剔除课表图片 URI，不上传原图、手机权限、朋友圈额度、聊天或账号令牌。
- 下载先校验 SHA-256 与备份结构，再展示恢复摘要；确认后先保存原记录的本机历史副本，再替换记录。
- 退出登录清除本机加密会话，保留本机计划。离线退出会明确说明云端会话尚未撤销。切换账号不会自动上传本机记录。
- 记录在传输中使用 TLS；本版本没有实现端到端加密，不应声称云服务维护者无法访问云端备份。

## 官方参考

- https://supabase.com/docs/guides/auth/passwords
- https://supabase.com/docs/guides/auth/auth-smtp
- https://supabase.com/docs/guides/getting-started/api-keys
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://supabase.github.io/auth/
