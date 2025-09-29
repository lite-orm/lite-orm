# lite-orm


<!-- 保护区开始 不要修改 -->
## `lite-orm` 架构介绍与愿景

### 模块
- lite-compiler 编译模块。编译期间把模板或者注解翻译为Java文件，后面会被编译成class。需要支持动态SQL转换为Java逻辑。顶层有接口可以自定义实现。
- lite-core 核心模块。责任链模式，处理连接Datasource，事务，参数绑定，执行，缓存
- lite-spring-boot-starter 和spring集成。autoconfig，spring 事务，对象管理，责任链构建。

### 依赖
- 使用 maven
- 使用 JDK 21

### 架构核心：编译时代码生成与运行时责任链

`lite-orm` 的核心理念是**“将运行时开销前置到编译时”**。我们放弃 MyBatis 在运行时依赖的反射和模板解析，转而借鉴 **MapStruct** 的思路，在编译期间直接将 SQL 逻辑和对象映射代码生成为纯粹的、可编译的 Java 代码。

整个 ORM 操作的生命周期被划分为两个阶段：

1.  **编译时**：一个**注解处理器**会根据配置执行compiler 它会根据compiler具体的实现插件把这些生成的代码包含了所有 SQL 语句的硬编码以及高效的对象映射逻辑。
2.  **运行时**：所有 ORM 操作都将通过一个**可插拔的责任链**来处理。每个操作（例如一次查询）会经历一系列的处理器：获取连接、执行 SQL、映射结果等。这种设计使得框架的每个部分都职责单一，且极易扩展。

### 目标与愿景

我们的目标是打造一个高性能、高扩展性、易于使用的 Java ORM 框架，成为 MyBatis 的有力替代品。

* **性能卓越**：通过消除运行时反射和解析，`lite-orm` 能够充分利用 **JIT 编译器**的优化，提供**接近原生 JDBC 的性能**。
* **高度可扩展**：基于责任链模式，开发者可以轻松地添加自定义功能，如缓存、SQL 审计、自定义数据源等，而无需修改框架核心。
* **简洁易用**：通过编译期生成代码，开发者可以享受高度扩展和极致性能的便利。同时，我们支持 MyBatis 风格的注解和 XML，为开发者提供平滑的迁移体验。
* **未来展望**：`lite-orm` 旨在成为一个社区驱动的开源项目。未来我们将支持更多数据库，引入更高级的查询语言，并持续优化性能，将其打造成 Java ORM 领域的下一个标杆。

<!-- 保护区结束 不要修改 -->

---

## 🎯 当前架构状态 ✅


#### 📦 统一架构
```
lite-orm/                # 单一模块（用户只需引入一个依赖）
├── SqlEngine           # 任务提交入口
├── SqlTask/SqlResult   # 任务和结果封装
├── processor/          # 5个专业处理器
│   ├── ConnectionProcessor   # 连接获取
│   ├── TransactionProcessor  # 事务管理
│   ├── ParameterProcessor    # 参数绑定
│   ├── ExecutionProcessor    # SQL执行
│   └── ResultProcessor       # 结果提取
├── LiteOrmProcessor    # 注解处理器
└── @Mapper/@Select     # MyBatis兼容注解
```

#### 🔄 工作流程
```
编译期：@Mapper接口 → LiteOrmProcessor → 生成MapperImpl.java（零反射硬编码）
运行时：MapperImpl → SqlEngine → 5个专业处理器 → 原生JDBC → 返回结果
```

#### 🎯 核心特性
- **零反射**：所有映射逻辑编译期生成
- **零配置**：约定优于配置，可选配置文件
- **MyBatis兼容**：@Mapper、@Select等注解完全兼容
- **单一依赖**：用户只需引入一个lite-orm依赖
- **高性能**：接近原生JDBC性能

#### 📊 核心组件状态表

| 组件 | 职责 | 状态 | 特点 |
|------|------|------|------|
| **SqlEngine** | 任务提交入口 | ✅ 完成 | 统一接口 |
| **SqlTask/SqlResult** | 任务结果封装 | ✅ 完成 | 类型安全 |
| **ConnectionProcessor** | 连接获取 | ✅ 完成 | 事务复用 |
| **TransactionProcessor** | 事务管理 | ✅ 完成 | 自动化 |
| **ParameterProcessor** | 参数绑定 | ✅ 完成 | 防SQL注入 |
| **ExecutionProcessor** | SQL执行 | ✅ 完成 | 纯JDBC |
| **ResultProcessor** | 结果提取 | ✅ 完成 | 零反射 |
| **LiteOrmProcessor** | 注解处理器 | ✅ 完成 | 编译期生成 |
| **MapperImplGenerator** | 代码生成 | 🚧 基础版 | 需要完善 |

---

## 🚀 下一步计划

### 🎯 Compiler模块完善（当前焦点）
- [ ] **方法参数解析**：支持复杂参数绑定
- [ ] **结果映射生成**：硬编码类型安全转换
- [ ] **SQL参数替换**：#{param}语法支持
- [ ] **XML编译器**：动态SQL转Java代码

### 🔬 功能验证
- [ ] 单元测试 + 集成测试
- [ ] 性能基准测试 vs MyBatis  
- [ ] Spring Boot集成测试


