import {resolve} from 'path'
import type {ConfigEnv, UserConfig} from 'vite'
import {loadEnv} from 'vite'
import {createVitePlugins} from './build/vite'
import {exclude, include} from "./build/vite/optimize"
// 当前执行node命令时文件夹的地址(工作目录)
const root = process.cwd()

// 路径查找
function pathResolve(dir: string) {
    return resolve(root, '.', dir)
}

// https://vitejs.dev/config/
export default ({command, mode}: ConfigEnv): UserConfig => {
    let env = {} as any
    const isBuild = command === 'build'
    if (!isBuild) {
        env = loadEnv((process.argv[3] === '--mode' ? process.argv[4] : process.argv[3]), root)
    } else {
        env = loadEnv(mode, root)
    }
    
    // 开发环境调试：打印代理配置
    if (!isBuild) {
        console.log('[Vite Config] 代理配置:');
        console.log('  VITE_BASE_URL:', env.VITE_BASE_URL || 'http://localhost:48080');
        console.log('  VITE_API_URL:', env.VITE_API_URL);
        console.log('  代理规则: /admin-api ->', env.VITE_BASE_URL || 'http://localhost:48080');
    }
    
    return {
        base: env.VITE_BASE_PATH,
        root: root,
        // 服务端渲染
        server: {
            port: env.VITE_PORT, // 端口号
            host: "0.0.0.0",
            open: env.VITE_OPEN === 'true',
            // 本地跨域代理
            proxy: {
              // 所有以 /admin-api 开头的请求，代理到网关
              // ⚠️ 不要加 rewrite！让完整路径透传给网关，网关会处理路径重写
              '/admin-api': {
                target: env.VITE_BASE_URL || 'http://localhost:48080', // 网关地址
                changeOrigin: true,
                secure: false,
                ws: true, // 支持 WebSocket（用于实时监控）
                // 保留完整路径 /admin-api/...，网关会根据路由规则转发到对应的服务
                configure: (proxy, _options) => {
                  // 启动时打印代理目标
                  console.log('[Vite Proxy] 🚀 代理已启用: /admin-api ->', _options.target);
                  
                  // 请求开始
                  proxy.on('proxyReq', (proxyReq, req) => {
                    const url = req.url || '';
                    const method = req.method || 'GET';
                    console.log(`[Vite Proxy] ➡️  ${method} ${url}`);
                    console.log(`[Vite Proxy] 🎯 转发到: ${_options.target}${url}`);
                  });
                  
                  // 收到响应
                  proxy.on('proxyRes', (proxyRes, req) => {
                    const url = req.url || '';
                    const status = proxyRes.statusCode || 0;
                    console.log(`[Vite Proxy] ⬅️  ${status} ${url}`);
                  });
                  
                  // 关键！捕获连接错误（如 ECONNRESET）
                  proxy.on('error', (err, req, res) => {
                    const url = req?.url || 'unknown';
                    const method = req?.method || 'unknown';
                    console.error(`[Vite Proxy] ❌ 严重错误 (可能后端未启动或崩溃):`);
                    console.error(`  - 错误: ${err.message}`);
                    console.error(`  - 错误代码: ${err.code || 'N/A'}`);
                    console.error(`  - 请求: ${method} ${url}`);
                    console.error(`  - 目标: ${_options.target}${url}`);
                    
                    // 给前端返回友好提示（可选）
                    if (res && !res.headersSent) {
                      try {
                        res.writeHead(502, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ 
                          error: '网关代理失败，请检查后端服务是否运行',
                          details: err.message 
                        }));
                      } catch (e) {
                        console.error('[Vite Proxy] 无法写入错误响应:', e);
                      }
                    }
                  });
                  
                  // 捕获连接关闭事件
                  proxy.on('close', (req, socket) => {
                    const url = req?.url || 'unknown';
                    console.warn(`[Vite Proxy] ⚠️  连接关闭: ${url}`);
                  });
                },
              },
              // 滤波器API代理
              ['/filter-api']: {
                target: 'http://localhost:48083',
                ws: false,
                changeOrigin: true,
              },
              // 健康检查API代理
              ['/actuator']: {
                target: 'http://localhost:48083',
                ws: false,
                changeOrigin: true,
              },
            },
        },
        // 项目使用的vite插件。 单独提取到build/vite/plugin中管理
        plugins: createVitePlugins(),
        css: {
            preprocessorOptions: {
                scss: {
                    additionalData: '@use "@/styles/variables.scss" as *;',
                    javascriptEnabled: true,
                    silenceDeprecations: ["legacy-js-api"], // 参考自 https://stackoverflow.com/questions/78997907/the-legacy-js-api-is-deprecated-and-will-be-removed-in-dart-sass-2-0-0
                }
            }
        },
        resolve: {
            extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.scss', '.css'],
            alias: [
                {
                    find: 'vue-i18n',
                    replacement: 'vue-i18n/dist/vue-i18n.cjs.js'
                },
                {
                    find: /\@\//,
                    replacement: `${pathResolve('src')}/`
                }
            ]
        },
        build: {
            minify: 'terser',
            outDir: env.VITE_OUT_DIR || 'dist',
            sourcemap: env.VITE_SOURCEMAP === 'true' ? 'inline' : false,
            // brotliSize: false,
            terserOptions: {
                compress: {
                    drop_debugger: env.VITE_DROP_DEBUGGER === 'true',
                    drop_console: env.VITE_DROP_CONSOLE === 'true'
                }
            },
            rollupOptions: {
                output: {
                    manualChunks: {
                      echarts: ['echarts'], // 将 echarts 单独打包，参考 https://gitee.com/yudaocode/yudao-ui-admin-vue3/issues/IAB1SX 讨论
                      'form-create': ['@form-create/element-ui'], // 参考 https://github.com/yudaocode/yudao-ui-admin-vue3/issues/148 讨论
                      'form-designer': ['@form-create/designer'],
                    }
                },
            },
        },
        optimizeDeps: {include, exclude}
    }
}
