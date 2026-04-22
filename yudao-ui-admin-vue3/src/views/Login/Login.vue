<template>
  <div
    :class="prefixCls"
    class="relative h-[100%] lt-md:px-10px lt-sm:px-10px lt-xl:px-10px lt-xl:px-10px"
  >
    <!-- 全屏粒子背景 Canvas -->
    <canvas ref="particleCanvas" class="login-particles-canvas"></canvas>

    <!-- 全局数据流弹幕层（覆盖整个页面，但在登录面板外作为背景） -->
    <div class="login-stream-layer hidden lg:block">
      <div
        v-for="(col, colIdx) in streamColumns"
        :key="colIdx"
        :class="['stream-col', `stream-col-${colIdx + 1}`]"
      >
        <div class="data-stream">
          <div
            v-for="(msg, idx) in col.primary"
            :key="`p-${colIdx}-${idx}`"
            class="item"
          >
            {{ msg }}
          </div>
        </div>
        <div class="data-stream data-stream-alt">
          <div
            v-for="(msg, idx) in col.alt"
            :key="`a-${colIdx}-${idx}`"
            class="item"
          >
            {{ msg }}
          </div>
        </div>
      </div>
    </div>

    <div
      class="relative mx-auto flex h-full max-w-7xl flex-col items-center justify-center py-24"
    >
      <!-- 背景高光装饰，增强两侧层次感，不影响布局 -->
      <div class="login-orbit login-orbit-left"></div>
      <div class="login-orbit login-orbit-right"></div>

      <!-- 左右侧卖点文案，增强整体饱满度 -->
      <div class="login-side-features login-side-features-left">
        <div class="login-side-title">实时工业监测</div>
        <div class="login-side-item">· 振动 / 声学 / 温度多源采集</div>
        <div class="login-side-item">· 故障智能识别与预警</div>
        <div class="login-side-item">· 在线设备健康评分</div>
      </div>
      <div class="login-side-features login-side-features-right">
        <div class="login-side-title">大数据处理分析</div>
        <div class="login-side-item">· 海量历史数据建模</div>
        <div class="login-side-item">· 多维趋势与关联分析</div>
        <div class="login-side-item">· 运维决策可视化驾驶舱</div>
      </div>

      <!-- 顶部品牌文案（仅文字，无图标） -->
      <div class="mb-8 text-center text-white">
        <div class="text-26px font-bold tracking-wide md:text-32px lg:text-34px">
          工业故障监测大数据处理平台
        </div>
        <div class="mt-3 text-12px opacity-85 md:text-13px">
          Real-time Fault Intelligence for Critical Infrastructure
        </div>
      </div>

      <!-- 中间区域：仅居中玻璃拟态登录面板 -->
      <div class="login-main relative mt-2 flex w-full items-center justify-center lg:mt-4">
        <div class="login-panel-wrapper flex items-center justify-center">
          <Transition appear enter-active-class="animate__animated animate__fadeInRight">
            <div class="login-form-card w-full max-w-560px lt-xl:max-w-520px lt-lg:max-w-480px">
              <!-- 顶部登录提示 -->
              <div class="mb-4">
                <div class="text-16px font-semibold text-[rgba(226,232,240,0.95)]">
                  登录您的企业账户
                </div>
              </div>

              <div class="mb-4 flex items-center justify-end">
                <LocaleDropdown class="text-[var(--el-text-color-primary)]" />
              </div>

              <!-- 登录表单内容 -->
              <!-- 账号登录 -->
              <LoginForm class="m-auto h-auto" />
              <!-- 手机登录 -->
              <MobileForm class="m-auto h-auto" />
              <!-- 二维码登录 -->
              <QrCodeForm class="m-auto h-auto" />
              <!-- 注册 -->
              <RegisterForm class="m-auto h-auto" />
              <!-- 三方登录 -->
              <SSOLoginVue class="m-auto h-auto" />
              <!-- 忘记密码 -->
              <ForgetPasswordForm class="m-auto h-auto" />

              <!-- 安全标识与认证信息 -->
              <div class="mt-5 text-11px text-[rgba(100,116,139,0.95)]">
                <div class="mb-1 flex items-center space-x-2">
                  <span class="shield-dot"></span>
                  <span>端到端加密 · 会话隔离 · 多因子认证支持</span>
                </div>
                <div class="mt-2 text-[rgba(148,163,184,0.95)]">
                  © 2026 SentinelCore Technologies
                  <br />
                  工信部认证 | ISO 27001 | 等保三级
                </div>
              </div>
            </div>
          </Transition>
        </div>

      </div>

      <!-- 底部版权与技术支持信息 -->
      <div class="login-footer mt-6 text-center text-11px">
        <div>© 2026 SentinelCore Technologies</div>
        <div class="mt-1">
          工信部认证 | ISO 27001 | 等保三级 | 专利号 ZL2025XXXXXX
        </div>
        <div class="mt-1">
          技术支持：工业智能监测平台团队 | 备案号：京ICP备XXXXXX号
        </div>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { useDesign } from '@/hooks/web/useDesign'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'

import { LoginForm, MobileForm, QrCodeForm, RegisterForm, SSOLoginVue, ForgetPasswordForm } from './components'

defineOptions({ name: 'Login' })

const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('login')

// 数据流弹幕基础文案与重复密度
const STREAM_DENSITY = 20

type StreamColumnBase = {
  primary: string[]
  alt: string[]
}

// 基础弹幕文案：按你的中文描述配置
const baseStreamMessages = [
  '滤波算法微服务',
  '声发射传感器',
  '高速数据采集',
  '实时数据传输',
  'K8S编排管理',
  'Docker容器化部署',
  '实时数据可视化',
  '设备管理',
  '告警管理'
]

const baseStreamColumns: StreamColumnBase[] = Array.from<StreamColumnBase>({ length: 5 }, () => ({
  primary: baseStreamMessages,
  alt: baseStreamMessages
}))

const repeatMessages = (msgs: string[], times: number): string[] => {
  const result: string[] = []
  for (let i = 0; i < times; i++) {
    result.push(...msgs)
  }
  return result
}

const streamColumns = baseStreamColumns.map((col) => ({
  primary: repeatMessages(col.primary, STREAM_DENSITY),
  alt: repeatMessages(col.alt, STREAM_DENSITY)
}))

// 粒子背景：模拟从边缘向中心流动的数据粒子
const particleCanvas = ref<HTMLCanvasElement | null>(null)

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  life: number
  maxLife: number
}

onMounted(() => {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const particles: Particle[] = []
  const PARTICLE_COUNT = 90
  const dpr = window.devicePixelRatio || 1
  let width = 0
  let height = 0
  const center = { x: 0, y: 0 }

  const resize = () => {
    const rect = canvas.getBoundingClientRect()
    width = rect.width * dpr
    height = rect.height * dpr
    canvas.width = width
    canvas.height = height
    center.x = width / 2
    center.y = height / 2
  }

  resize()
  window.addEventListener('resize', resize)

  const createParticle = (): Particle => {
    // 从四边之一生成
    const edge = Math.floor(Math.random() * 4)
    let x = 0
    let y = 0
    const margin = 40 * dpr

    if (edge === 0) {
      // 顶部
      x = Math.random() * width
      y = -margin
    } else if (edge === 1) {
      // 底部
      x = Math.random() * width
      y = height + margin
    } else if (edge === 2) {
      // 左侧
      x = -margin
      y = Math.random() * height
    } else {
      // 右侧
      x = width + margin
      y = Math.random() * height
    }

    // 指向中心的方向，加一点随机偏移
    let angle = Math.atan2(center.y - y, center.x - x)
    angle += (Math.random() - 0.5) * 0.7
    const speed = (0.25 + Math.random() * 0.45) * dpr

    return {
      x,
      y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      size: (1 + Math.random() * 2) * dpr,
      life: 0,
      maxLife: 500 + Math.random() * 400
    }
  }

  for (let i = 0; i < PARTICLE_COUNT; i++) {
    particles.push(createParticle())
  }

  let animationId: number

  const render = () => {
    animationId = requestAnimationFrame(render)

    if (!width || !height) return

    ctx.clearRect(0, 0, width, height)
    ctx.globalCompositeOperation = 'lighter'

    for (let i = 0; i < particles.length; i++) {
      const p = particles[i]
      p.x += p.vx
      p.y += p.vy
      p.life += 1

      // 朝中心靠拢的轻微加速
      const dx = center.x - p.x
      const dy = center.y - p.y
      const dist = Math.sqrt(dx * dx + dy * dy) || 1
      const pull = 0.0008 * dpr
      p.vx += (dx / dist) * pull
      p.vy += (dy / dist) * pull

      // 超出寿命或离开画布太远则重置
      if (p.life > p.maxLife || p.x < -200 * dpr || p.x > width + 200 * dpr || p.y < -200 * dpr || p.y > height + 200 * dpr) {
        particles[i] = createParticle()
        continue
      }

      // 中央“数据漩涡”区域，粒子更亮
      const toCenter = Math.min(1, Math.hypot(p.x - center.x, p.y - center.y) / (Math.min(width, height) * 0.6))
      const alpha = (1 - toCenter) * 0.85 + 0.15

      ctx.beginPath()
      const gradient = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size * 4)
      gradient.addColorStop(0, `rgba(0, 193, 255, ${alpha})`)
      gradient.addColorStop(0.4, `rgba(0, 245, 255, ${alpha * 0.8})`)
      gradient.addColorStop(1, 'rgba(0, 193, 255, 0)')
      ctx.fillStyle = gradient
      ctx.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  render()

  onBeforeUnmount(() => {
    cancelAnimationFrame(animationId)
    window.removeEventListener('resize', resize)
  })
})
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-login;

.#{$prefix-cls} {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at top left, #0a0f1d 0, #0d1b2a 40%, #050816 100%);

  &::before {
    position: absolute;
    inset: 0;
    background-image: url('@/assets/svgs/login-bg.svg');
    background-position: center right;
    background-repeat: no-repeat;
    background-size: 720px auto;
    opacity: 0.16;
    content: '';
    pointer-events: none;
  }
}

.login-particles-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
}

.login-form-card {
  border-radius: 28px;
  padding: 30px 40px 26px;
  background: rgba(15, 23, 42, 0.75);
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.9),
    0 0 0 1px rgba(0, 193, 255, 0.35);
  border: 1px solid rgba(0, 193, 255, 0.3);
  backdrop-filter: blur(12px);
  color: #ffffff;
  transition:
    box-shadow 0.25s ease,
    transform 0.25s ease,
    border-color 0.25s ease;

  :deep(.el-form-item__label) {
    color: rgba(191, 219, 254, 0.92);
  }

  :deep(.el-input__wrapper) {
    background-color: #161e2e;
    box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.9) inset;
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-input__wrapper.is-active) {
    box-shadow:
      0 0 0 1px rgba(0, 193, 255, 0.9) inset,
      0 0 12px rgba(0, 193, 255, 0.5);
  }

  :deep(.el-input__inner) {
    color: #e5e7eb;
  }

  :deep(.el-button--primary) {
    background-image: linear-gradient(90deg, #00c1ff, #0077ff);
    border-color: transparent;
    box-shadow: 0 0 15px rgba(0, 193, 255, 0.55);
  }

  :deep(.el-button--primary:hover),
  :deep(.el-button--primary:focus) {
    box-shadow:
      0 0 20px rgba(0, 245, 255, 0.75),
      0 0 0 1px rgba(0, 245, 255, 0.9);
    filter: brightness(1.05);
  }
}

.login-form-card:hover {
  transform: translateY(-4px);
  box-shadow:
    0 30px 90px rgba(0, 0, 0, 0.95),
    0 0 0 1px rgba(0, 245, 255, 0.6);
  border-color: rgba(0, 245, 255, 0.6);
}

.login-footer {
  opacity: 0.75;
}

.login-illustration-wrapper img {
  filter: drop-shadow(0 16px 40px rgba(15, 23, 42, 0.8));
}

.login-orbit {
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(22px);
  opacity: 0.6;
}

.login-orbit-left {
  left: 4%;
  top: 52%;
  transform: translate(-10%, -40%);
  background: radial-gradient(circle, rgba(56, 189, 248, 0.5), transparent 60%);
}

.login-orbit-right {
  right: 4%;
  top: 42%;
  transform: translate(10%, -30%);
  background: radial-gradient(circle, rgba(251, 191, 36, 0.45), transparent 60%);
}

.login-side-features {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  max-width: 220px;
  font-size: 12px;
  color: rgba(226, 232, 240, 0.9);
  line-height: 1.6;
}

.login-side-features-left {
  left: 4%;
  text-align: left;
}

.login-side-features-right {
  right: 4%;
  text-align: right;
}

.login-side-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.login-side-item {
  opacity: 0.8;
}

.login-main {
  position: relative;
  z-index: 1;
}

.login-stream-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  mix-blend-mode: screen;
  opacity: 0.85;
  display: flex;
  justify-content: space-between;
  align-items: stretch;
}

.stream-col {
  position: relative;
  width: 220px;
}

.stream-col-1 {
  transform: translateY(-4%);
}

.stream-col-2 {
  transform: translateY(6%);
}

.stream-col-3 {
  transform: translateY(0%);
}

.stream-col-4 {
  transform: translateY(-6%);
}

.stream-col-5 {
  transform: translateY(8%);
}

.login-hero-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.login-particle {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: rgba(0, 245, 255, 0.85);
  opacity: 0.65;
  box-shadow: 0 0 12px rgba(0, 245, 255, 0.9);
  animation: floatParticle 14s linear infinite;
}

.login-particle-1 {
  top: 12%;
  left: 10%;
}

.login-particle-2 {
  top: 26%;
  right: 8%;
  animation-duration: 18s;
}

.login-particle-3 {
  top: 52%;
  left: 18%;
  animation-duration: 16s;
}

.login-particle-4 {
  bottom: 16%;
  right: 14%;
  animation-duration: 20s;
}

.login-particle-5 {
  bottom: 10%;
  left: 30%;
  animation-duration: 22s;
}

.login-particle-6 {
  top: 38%;
  right: 32%;
  animation-duration: 19s;
}

@keyframes floatParticle {
  0% {
    transform: translate3d(0, 0, 0);
    opacity: 0.2;
  }
  40% {
    opacity: 0.8;
  }
  100% {
    transform: translate3d(40px, -60px, 0);
    opacity: 0.1;
  }
}

.login-holo {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 10px auto 0;
  width: min(280px, 80%);
  aspect-ratio: 4 / 3;
}

.login-holo-ring {
  position: absolute;
  bottom: 0;
  width: 100%;
  height: 52%;
  border-radius: 999px;
  background: radial-gradient(circle at center, rgba(0, 245, 255, 0.7), transparent 70%);
  opacity: 0.7;
  filter: blur(2px);
}

.login-holo-core {
  position: relative;
  width: 68%;
  height: 72%;
  border-radius: 24px;
  border: 1px solid rgba(0, 193, 255, 0.6);
  background:
    radial-gradient(circle at top left, rgba(0, 193, 255, 0.28), transparent 55%),
    linear-gradient(145deg, rgba(15, 23, 42, 0.95), rgba(15, 23, 42, 0.6));
  box-shadow:
    0 18px 55px rgba(15, 23, 42, 0.9),
    0 0 32px rgba(0, 193, 255, 0.6);
  overflow: hidden;
  animation: floatCore 7s ease-in-out infinite;
}

.login-holo-core::before {
  position: absolute;
  inset: 12px;
  border-radius: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.6);
  background: radial-gradient(circle at center, rgba(15, 118, 230, 0.32), transparent 65%);
  content: '';
  opacity: 0.85;
}

.login-holo-gear {
  position: absolute;
  left: 12%;
  top: 20%;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 2px solid rgba(148, 163, 184, 0.9);
  box-shadow: 0 0 18px rgba(148, 163, 184, 0.9);
  opacity: 0.9;
}

.login-holo-sensor {
  position: absolute;
  right: 10%;
  bottom: 16%;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  border: 1px solid rgba(0, 245, 255, 0.85);
  box-shadow:
    0 0 18px rgba(0, 245, 255, 0.9),
    0 0 32px rgba(0, 193, 255, 0.6);
  opacity: 0.95;
}

@keyframes floatCore {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0, -10px, 0);
  }
}

.login-metrics {
  position: relative;
  z-index: 1;
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  font-size: 11px;
}

.login-metric-card {
  border-radius: 14px;
  padding: 10px 10px 9px;
  background: radial-gradient(circle at top left, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.88));
  border: 1px solid rgba(30, 64, 175, 0.85);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.9);
}

.login-metric-label {
  margin-bottom: 2px;
  color: rgba(148, 163, 184, 0.95);
}

.login-metric-value {
  margin-bottom: 2px;
  font-size: 16px;
  font-weight: 600;
}

.login-metric-desc {
  font-size: 10px;
  color: rgba(148, 163, 184, 0.9);
}

.login-hero-tagline {
  position: relative;
  z-index: 1;
  margin-top: 18px;
  text-align: left;
  font-size: 12px;
  color: rgba(226, 232, 240, 0.9);
}

.login-hero-main {
  margin-bottom: 2px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-hero-sub {
  font-size: 11px;
  opacity: 0.85;
}

.shield-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: radial-gradient(circle at top, #00c1ff, #0077ff);
  box-shadow: 0 0 10px rgba(0, 193, 255, 0.7);
}

.login-footer {
  opacity: 0.8;
  color: #8899aa;
}

.data-stream {
  position: relative;
  overflow: hidden;
  height: 260px;
  padding-left: 2px;
  font-family: 'Monaco', 'Courier New', ui-monospace, SFMono-Regular, Menlo, Monaco,
    Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
  color: #00c1ff;
}

.data-stream .item {
  position: relative;
  margin-bottom: 8px;
  opacity: 0.6;
  filter: blur(1px);
  animation: float-up 8s linear infinite;
}

.data-stream .item:nth-child(2) {
  animation-delay: -2s;
  color: #00f5ff;
}

.data-stream .item:nth-child(3) {
  animation-delay: -4s;
  color: #00bfff;
}

.data-stream .item:nth-child(4) {
  animation-delay: -6s;
}

.data-stream .item:nth-child(5) {
  animation-delay: -1s;
}

.data-stream-alt {
  margin-top: 10px;
  transform: translateX(12px);
}

.data-stream-alt .item {
  animation-duration: 11s;
}

@keyframes float-up {
  0% {
    transform: translate3d(0, 40px, 0);
    opacity: 0;
  }
  15% {
    opacity: 0.6;
  }
  85% {
    opacity: 0.6;
  }
  100% {
    transform: translate3d(0, -40px, 0);
    opacity: 0;
  }
}
</style>

<style lang="scss">
.dark .login-form {
  .el-divider__text {
    background-color: var(--login-bg-color);
  }

  .el-card {
    background-color: var(--login-bg-color);
  }
}
</style>
