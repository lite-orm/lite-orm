# 开源项目 GitHub Pages 官网与文档案例调研

## 结论摘要

`lite-orm.github.io` 完全可以采用“独立静态站点仓库 + GitHub Actions 发布”的方式。项目文档应以 Markdown 为 canonical source，必要时使用 MDX（组件化首页、交互示例）；构建产物是静态 HTML/CSS/JS，便于 CDN、搜索引擎和 AI agent 抓取。

成熟项目通常把“品牌首页”和“开发者文档”作为同一站点的两个入口：首页负责一句话定位、代码/性能卖点和快速开始，文档负责可搜索的分层导航、版本/语言切换和 API 细节。下面的案例既包含 `*.github.io`，也包含后来迁移到自定义域名的项目，后者可作为视觉和信息架构参考。

## 代表案例

| 项目与站点 | 站点/文档实现 | 值得借鉴的做法 | 对 LiteORM 的启示 |
| --- | --- | --- | --- |
| **mdBook** — [rust-lang.github.io/mdBook](https://rust-lang.github.io/mdBook/) | Rust mdBook 直接把 Markdown 章节生成静态书籍；官方 `guide/book.toml` 开启内置搜索、代码 playground、编辑链接和稳定 `site-url`；CI 脚本发布到 `gh-pages`。 | 目录即导航，URL 稳定；代码示例可编辑运行；搜索索引随构建生成。 | API/迁移手册可保持 Markdown；为每个示例保留可复制、可验证代码；生成 `llms.txt` 时可直接复用章节结构。 |
| **FlatBuffers** — [google.github.io/flatbuffers](https://google.github.io/flatbuffers/)（现也有 [flatbuffers.dev](https://flatbuffers.dev/)） | 官方 [`docs/mkdocs.yml`](https://github.com/google/flatbuffers/blob/master/docs/mkdocs.yml) 使用 MkDocs Material；启用深色模式、导航展开、代码注释、内容 tabs、编辑链接和 redirects。 | 首页使用卡片/图标突出性能卖点；文档支持 tabs、版本兼容说明和移动端导航。 | “科技感”可通过深色/浅色主题、性能数字卡片、代码 tabs 实现，不必牺牲 Markdown 可读性。 |
| **gRPC C Core API** — [grpc.github.io/grpc/core](https://grpc.github.io/grpc/core/) | gRPC 仓库的 [`doc/`](https://github.com/grpc/grpc/tree/master/doc) 与 Doxygen 配置生成 API 参考并部署到 GitHub Pages。 | API 参考与概念文档分离；类、函数、宏有可链接锚点，适合 IDE/搜索索引。 | LiteORM 可将“概念/教程”和“生成 API 参考”分层；为每个配置键、注解和异常提供稳定锚点。 |
| **Kustomize** — [kubernetes-sigs.github.io/kustomize](https://kubernetes-sigs.github.io/kustomize/) | Kubernetes SIG 项目使用独立文档站点和版本化发布流程（仓库中的 [`site/`](https://github.com/kubernetes-sigs/kustomize/tree/master/site) 与 Pages/CI 配置）。 | 面向任务的导航（入门、任务、参考、贡献）；版本与 CLI 版本保持一致。 | 迁移 skill、Spring Boot、多 DataSource 应按任务组织，而不是按内部模块组织；文档 URL 需与发行版本绑定。 |
| **React（历史 GitHub Pages）** — [facebook.github.io/react](https://facebook.github.io/react/)（现为 [react.dev](https://react.dev/)） | 早期站点是 React 静态站点，后续迁移到独立域名和新的 React 文档应用；源码及部署历史保留在 [`facebook/react`](https://github.com/facebook/react) 的站点相关目录/提交中。 | 从“库介绍 + API”升级为“学习路径 + 交互式教程”；首页以清晰 CTA 引导安装和开始学习。 | LiteORM 首页应先回答“何时使用、零运行时代理、编译期生成”三个问题，再引导 Quick Start；教程按角色和任务拆分。 |
| **Docusaurus** — [docusaurus.io](https://docusaurus.io/)（官方 showcase） | Docusaurus 自身使用 React/MDX，官方支持 GitHub Pages Actions、i18n、文档版本和 Algolia/本地搜索；源码 [`facebook/docusaurus`](https://github.com/facebook/docusaurus)。 | 首页可用 React 组件做高质感 hero、代码窗口和动画；文档仍以 Markdown/MDX 为主。 | 采用 Docusaurus 可同时满足高端首页、Markdown 文档、多语言和版本化；复杂视觉仅放在首页组件，正文保持纯 Markdown。 |
| **VitePress** — [vitepress.dev](https://vitepress.dev/)（官方 showcase） | Vue 驱动的 Markdown-first 静态站点；官方提供 GitHub Pages 部署、默认主题本地搜索和 i18n 指南；源码 [`vuejs/vitepress`](https://github.com/vuejs/vitepress)。 | 默认主题极简、加载快；Markdown 页面可渐进加入 Vue 组件。 | 若优先追求轻量和极快构建，VitePress 是备选；但多版本文档和复杂 i18n 需自行约定目录/构建策略。 |

## 视觉与信息架构模式

1. **Hero + 明确 CTA**：一句话价值主张，紧接 `Get Started`、GitHub、Maven 坐标和可复制代码。避免首页只有项目 Logo 或长篇背景介绍。
2. **可量化卖点卡片**：FlatBuffers 等项目用图标卡片展示零解析、低内存、兼容性等特性。LiteORM 可展示“编译期 Mapper”“无运行时代理”“Spring 多 DataSource”等事实性能力，避免未经基准测试的性能数字。
3. **任务型文档导航**：Getting Started、Concepts、Guides、Reference、Migration、Examples、Contributing。把 Spring Boot、多 DataSource 和 MyBatis 迁移作为用户任务入口。
4. **代码优先**：每个概念页先给最小可运行片段，再解释边界；示例由 CI 编译，页面显示对应版本。
5. **深色/浅色与可访问性**：提供主题切换、键盘可达导航、足够对比度和 `prefers-reduced-motion`；科技感来自排版、留白和代码块，不依赖重型视频背景。

## Markdown、国际化与 AI agent 输出

- Markdown 是规范文档源；MDX 只用于首页组件、交互示例和 Tabs。不要把关键内容只放在客户端渲染组件中。
- 规划 `/en/` 和 `/zh-Hans/` 稳定前缀；语言切换使用可爬取链接。Docusaurus 的 locale 目录和版本目录可直接映射到 URL。
- 规划 `/docs/0.1/`、`/docs/current/` 等版本前缀；发布新版本时保留旧版本链接，避免示例 URL 失效。
- 构建并发布 `sitemap.xml`、`robots.txt`、每页 canonical URL；额外生成根目录 `llms.txt` 和按章节拆分的 Markdown 镜像，内容包含版本、语言和源文件链接。
- 为标题、配置键、注解和异常保留确定性锚点；代码块标注语言和依赖版本，方便检索与自动修复。

## 对 `lite-orm.github.io` 的建议落地方案

1. 建立组织 Pages 仓库 `lite-orm/lite-orm.github.io`，使用 GitHub Actions 构建并发布 `gh-pages` 或 `pages` artifact；仓库根放置 `CNAME`（若以后绑定自定义域名）。
2. 首选 Docusaurus：React 首页 + Markdown/MDX 文档、内置 i18n/版本管理和可选搜索；将 LiteORM 现有 `docs/` 与 `lite-orm-examples/` 通过同步脚本/链接纳入站点，避免复制后漂移。
3. 首页采用深色科技感但保持静态可访问：Hero、架构示意、代码窗口、能力卡片、生态链接、快速开始 CTA；不引入运行时数据库或登录系统。
4. CI 门禁包括 Markdown 链接检查、示例编译、英文/简体中文页面覆盖率、生成 `llms.txt`、sitemap 和 Lighthouse/可访问性检查。

## 一手来源

- [mdBook guide/book.toml](https://github.com/rust-lang/mdBook/blob/main/guide/book.toml)、[发布到 GitHub Pages 的脚本](https://github.com/rust-lang/mdBook/blob/main/ci/publish-guide.sh)
- [FlatBuffers docs/mkdocs.yml](https://github.com/google/flatbuffers/blob/master/docs/mkdocs.yml)、[FlatBuffers 文档首页](https://google.github.io/flatbuffers/)
- [gRPC doc 目录](https://github.com/grpc/grpc/tree/master/doc)、[gRPC Core API Pages](https://grpc.github.io/grpc/core/)
- [Kustomize site 目录](https://github.com/kubernetes-sigs/kustomize/tree/master/site)、[Kustomize Pages](https://kubernetes-sigs.github.io/kustomize/)
- [React 仓库](https://github.com/facebook/react)、[历史 Pages 地址](https://facebook.github.io/react/)、[当前文档](https://react.dev/)
- [Docusaurus 文档](https://docusaurus.io/docs)、[Docusaurus 仓库](https://github.com/facebook/docusaurus)
- [VitePress 部署](https://vitepress.dev/guide/deploy)、[VitePress i18n](https://vitepress.dev/guide/i18n)、[VitePress 仓库](https://github.com/vuejs/vitepress)

