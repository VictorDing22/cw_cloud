# 🏭 工业健康监测云平台

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2021-blue.svg" alt="Spring Cloud">
  <img src="https://img.shields.io/badge/Spring%20Boot-2.7.18-blue.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.2-brightgreen.svg" alt="Vue">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License">
</p>

## 📋 项目简介

**工业健康监测云平台**是一个基于 Spring Cloud Alibaba 微服务架构的工业设备健康监测与智能分析系统。本系统专注于声发射（AE）数据采集、滤波算法处理、振动数据分析、AI模式识别等工业监测领域的核心功能，为航空航天、桥梁建筑、文物保护、工业制造等行业提供智能化的设备健康管理解决方案。

### 🎯 核心特性

- **20种滤波算法微服务**: 卡尔曼、LMS、NLMS、均值、中值、高斯、巴特沃斯、切比雪夫、FIR、IIR、维纳、小波、形态学、双边、SG平滑、粒子、扩展卡尔曼、无迹卡尔曼、自适应陷波、复合滤波
- **实时数据监控**: 声发射数据实时采集、处理与可视化，支持6类关键参数监测
- **智能告警系统**: 多级告警机制，支持严重、警告、信息三级告警管理
- **振动数据分析**: 时域/频域分析、FFT变换、三轴振动对比、异常检测
- **声发射评级**: GB/T 18182标准声发射评级，设备健康度评估与寿命预测
- **多参数相关分析**: 多维度参数关联分析、散点图、热力图、线性回归
- **AI模式识别**: 支持神经网络、SVM、随机森林、KNN等多种AI算法的模式训练与识别
- **行业应用场景**: 航空航天、桥梁监测、文物保护、工业制造等典型应用案例

### 🌟 技术亮点

- **微服务架构**: Spring Cloud Alibaba + Nacos + Gateway + Sentinel
- **实时通信**: WebSocket + Redis 实现实时数据推送与集群支持
- **高性能存储**: MySQL + Redis + 时序数据库（可选）
- **智能算法**: 20种滤波算法 + 4种AI模型 + 多维度数据分析
- **可视化展示**: ECharts 动态图表，支持时序图、频谱图、热力图、3D可视化
- **Docker部署**: 一键启动脚本，支持 Docker Compose 快速部署

---

## 🚀 快速开始

### 环境要求

- **JDK**: 1.8+
- **Node.js**: 16.0+
- **MySQL**: 5.7+ / 8.0+
- **Redis**: 5.0+
- **Maven**: 3.6+
- **Docker** (可选): 20.0+

### 快速启动（推荐）

使用一键启动脚本快速部署所有服务：

```bash
# 1. 克隆项目
git clone https://github.com/sheng-dev/CW_Cloud.git
cd CW_Cloud

# 2. 启动后端服务（backend分支）
git checkout backend
cd yudao-cloud
chmod +x quick-start.sh
./quick-start.sh

# 3. 启动前端服务（frontend分支）
git checkout frontend
cd yudao-ui-admin-vue3
npm install
npm run dev
```

### Docker 部署

```bash
# 启动基础设施服务（MySQL、Redis、Nacos）
docker-compose up -d

# 启动微服务（可选）
docker-compose -f docker-compose-apps.yml up -d
```

### 手动启动

#### 后端服务

```bash
# 1. 导入数据库
# 执行 sql/mysql/yudao-cloud.sql

# 2. 修改配置
# 编辑各模块的 application-dev.yml，配置数据库和Redis连接

# 3. 启动 Nacos（配置中心和注册中心）
# 参考: https://nacos.io/zh-cn/docs/quick-start.html

# 4. 依次启动各微服务
mvn clean install -DskipTests
cd yudao-gateway && mvn spring-boot:run
cd yudao-module-system && mvn spring-boot:run
cd yudao-module-infra && mvn spring-boot:run
cd yudao-module-iot && mvn spring-boot:run
cd yudao-module-filter && mvn spring-boot:run
```

#### 前端服务

```bash
# 1. 安装依赖
cd yudao-ui-admin-vue3
npm install

# 2. 启动开发服务器
npm run dev

# 3. 访问系统
# 浏览器访问: http://localhost:3000
# 默认账号: admin / admin123
```

---

## 📦 系统架构

### 微服务模块

```
yudao-cloud
├── yudao-dependencies         # Maven 依赖版本管理
├── yudao-framework            # 框架核心封装
├── yudao-gateway              # API 网关服务
├── yudao-module-system        # 系统管理模块（用户、角色、权限、菜单）
├── yudao-module-infra         # 基础设施模块（文件、配置、定时任务）
├── yudao-module-iot           # 物联网核心模块
│   ├── 设备管理               # 设备/产品/分组管理
│   ├── 声发射数据             # AE数据采集与监控
│   ├── 告警管理               # 多级告警与通知
│   ├── 声发射评级             # GB/T 18182评级标准
│   ├── 振动数据               # 振动监测与FFT分析
│   ├── 相关图分析             # 多参数关联分析
│   ├── 模式识别               # AI模式识别与训练
│   └── 应用场景               # 行业应用案例
└── yudao-module-filter        # 滤波算法微服务
    ├── 自适应滤波（卡尔曼、LMS、NLMS、粒子、EKF、UKF）
    ├── 频域滤波（巴特沃斯、切比雪夫、FIR、IIR）
    ├── 空域滤波（均值、中值、高斯、双边）
    ├── 变换域滤波（小波、维纳、形态学）
    └── 高级滤波（SG平滑、自适应陷波、复合滤波）
```

### 技术栈

**后端技术**

| 技术                    | 版本      | 说明                     |
| ----------------------- | --------- | ------------------------ |
| Spring Cloud Alibaba    | 2021.0.4.0 | 微服务框架               |
| Spring Boot             | 2.7.18    | 基础框架                 |
| Nacos                   | 2.3.2     | 配置中心 & 注册中心      |
| Gateway                 | 3.4.1     | 服务网关                 |
| Sentinel                | 1.8.6     | 服务保障                 |
| MyBatis Plus            | 3.5.7     | ORM 框架                 |
| Redis                   | 5.0+      | 缓存 & 消息队列          |
| MySQL                   | 5.7 / 8.0+ | 关系型数据库             |
| WebSocket               | -         | 实时通信                 |

**前端技术**

| 技术           | 版本   | 说明                 |
| -------------- | ------ | -------------------- |
| Vue            | 3.2    | 前端框架             |
| TypeScript     | 4.9    | 类型系统             |
| Element Plus   | 2.4    | UI 组件库            |
| ECharts        | 5.4    | 数据可视化           |
| Axios          | 1.6    | HTTP 客户端          |
| Pinia          | 2.1    | 状态管理             |

---

## 🎨 功能模块

### 1. 实时数据监控

- **声发射数据监控**: 6类关键参数实时图表（持续时间、振铃计数、上升时间、上升计数、幅度、平均信号电平）
- **数据表格**: 分页展示、多选操作、详情查看、数据导出（CSV）
- **设备筛选**: 按产品/设备/参数/时间范围筛选
- **图表交互**: 支持缩放、拖拽、数据悬停、工具栏操作

### 2. 设备管理

- **设备列表**: 50+设备，支持在线/离线/告警状态监控
- **设备操作**: 启动/停止采集、重启设备、设备配置、删除设备
- **批量操作**: 批量启动、批量停止、批量删除
- **设备详情**: 设备信息、AE参数配置、滤波配置

### 3. 告警管理

- **告警统计**: 实时统计总告警数、严重告警、警告告警、信息告警
- **告警列表**: 100+历史告警记录
- **告警处理**: 告警确认、处理记录、批量处理
- **告警级别**: 严重（红色）、警告（橙色）、信息（蓝色）

### 4. 滤波器实验

- **20种算法**: 支持自适应、频域、空域、变换域、高级滤波
- **参数配置**: 每种算法独立参数配置界面
- **对比实验**: 多算法同时执行，自动评估最优算法
- **性能评估**: MSE误差、处理时间、信噪比提升

### 5. 声发射评级

- **GB/T 18182标准**: 符合国标的声发射评级算法
- **健康度评估**: A/B/C/D四级评级体系
- **寿命预测**: 基于历史数据的设备剩余寿命预测
- **评级趋势**: 历史评级趋势图、分布饼图

### 6. 振动数据分析

- **时域分析**: 实时波形显示、统计特征（RMS、峰值、均值、标准差）
- **频域分析**: FFT频谱分析、频率分布
- **三轴对比**: X/Y/Z三轴振动对比图
- **异常检测**: 基于阈值的异常检测与告警

### 7. 多参数相关分析

- **散点图**: 任意两参数关联分析
- **皮尔逊相关**: 相关系数计算
- **线性回归**: 趋势线拟合与方程显示
- **热力图**: 多参数相关性矩阵可视化

### 8. AI模式识别

- **模式库管理**: 模式定义、特征提取、模板管理
- **AI模型训练**: 神经网络、SVM、随机森林、KNN
- **实时识别**: 实时数据模式匹配
- **批量识别**: 历史数据批量分析
- **性能评估**: 准确率、混淆矩阵、ROC曲线

### 9. 行业应用

- **航空航天**: 飞机结构健康监测、发动机状态监控
- **桥梁监测**: 桥梁结构安全评估、疲劳裂纹检测
- **文物保护**: 古建筑监测、文物损伤评估
- **工业制造**: 压力容器、管道、储罐健康监测

---

## 📖 文档目录

项目文档位于 `docs` 目录：

- [工业健康监测系统-完整API文档.md](docs/工业健康监测系统-完整API文档.md) - 完整的后端API接口文档
- [系统使用指南.md](docs/系统使用指南.md) - 用户操作手册
- [Docker部署说明.md](docs/Docker部署说明.md) - Docker部署指南
- [20个滤波算法微服务架构设计.md](docs/20个滤波算法微服务架构设计.md) - 滤波算法技术文档

---

## 🔧 配置说明

### 后端配置

主要配置文件：`application-dev.yml`

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yudao-cloud?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

# Redis配置
  redis:
    host: localhost
    port: 6379
    password: 

# Nacos配置
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
```

### 前端配置

主要配置文件：`.env.development`

```env
# API 地址
VITE_API_URL=http://localhost:48080

# WebSocket 地址
VITE_WS_URL=ws://localhost:48080/ws
```

---

## 🛠️ 开发指南

### 后端开发

1. **新增微服务模块**
   - 在 `yudao-module-*` 下创建新模块
   - 添加到 `pom.xml` 依赖管理
   - 配置 Nacos 注册

2. **新增API接口**
   - Controller 层：RESTful 风格
   - Service 层：业务逻辑
   - DAO 层：MyBatis Plus

3. **WebSocket 实时推送**
   - 参考 `IoTWebSocketHandler`
   - 支持设备订阅与广播

### 前端开发

1. **新增页面**
   - 在 `src/views` 下创建 Vue 组件
   - 在 `src/router` 添加路由配置
   - 在 `src/api` 添加API调用

2. **状态管理**
   - 使用 Pinia 进行全局状态管理
   - 参考 `src/stores`

3. **组件开发**
   - 使用 Element Plus 组件库
   - 使用 ECharts 进行数据可视化

---

## 📊 系统截图

### 工业监控界面
![工业监控](docs/screenshots/industrial-monitor.png)

### 声发射数据监控
![声发射监控](docs/screenshots/ae-data-monitor.png)

### 滤波器实验
![滤波器实验](docs/screenshots/filter-experiment.png)

### 振动数据分析
![振动分析](docs/screenshots/vibration-analysis.png)

---

## 🤝 贡献指南

欢迎贡献代码、提出问题和建议！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

- ✅ 个人与企业可 100% 免费使用
- ✅ 可自由修改、分发、商用
- ✅ 无需保留作者信息

---

## 📞 联系方式

- **GitHub**: https://github.com/sheng-dev/CW_Cloud
- **Issues**: 欢迎提交问题和建议

---

## 🙏 致谢

本项目基于 [YuDao Cloud](https://gitee.com/zhijiantianya/yudao-cloud) 框架开发，感谢芋道团队提供的优秀开源框架！

特别感谢以下开源项目：

- [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [ECharts](https://echarts.apache.org/)
- [MyBatis Plus](https://baomidou.com/)

---

<p align="center">
  <sub>如果这个项目对您有帮助，请给我们一个 ⭐️ Star！</sub>
</p>
