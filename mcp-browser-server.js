#!/usr/bin/env node

/**
 * AI Chat Plugin MCP Browser Server
 * 允许 AI 访问和分析网页内容
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import puppeteer from 'puppeteer';
import axios from 'axios';
import * as cheerio from 'cheerio';

class BrowserServer {
  constructor() {
    this.server = new Server(
      {
        name: 'ai-chat-plugin-browser',
        version: '1.0.0',
      },
      {
        capabilities: {
          tools: {},
        },
      }
    );

    this.browser = null;
    this.setupToolHandlers();
    
    // 错误处理
    this.server.onerror = (error) => console.error('[MCP Error]', error);
    process.on('SIGINT', () => this.cleanup());
  }

  setupToolHandlers() {
    // 列出可用工具
    this.server.setRequestHandler(ListToolsRequestSchema, async () => ({
      tools: [
        {
          name: 'visit_webpage',
          description: '访问指定网页并获取内容',
          inputSchema: {
            type: 'object',
            properties: {
              url: {
                type: 'string',
                description: '要访问的网页URL',
              },
              wait_for_selector: {
                type: 'string',
                description: '等待特定元素加载（可选）',
              },
              screenshot: {
                type: 'boolean',
                description: '是否截图（可选）',
                default: false,
              },
            },
            required: ['url'],
          },
        },
        {
          name: 'analyze_page_content',
          description: '分析网页内容结构',
          inputSchema: {
            type: 'object',
            properties: {
              url: {
                type: 'string',
                description: '要分析的网页URL',
              },
              focus_area: {
                type: 'string',
                description: '重点分析的区域（如：表单、导航、内容区域等）',
              },
            },
            required: ['url'],
          },
        },
        {
          name: 'check_api_endpoints',
          description: '检查网页中的API端点和网络请求',
          inputSchema: {
            type: 'object',
            properties: {
              url: {
                type: 'string',
                description: '要检查的网页URL',
              },
              duration: {
                type: 'number',
                description: '监控时长（秒）',
                default: 10,
              },
            },
            required: ['url'],
          },
        },
        {
          name: 'test_form_interaction',
          description: '测试网页表单交互',
          inputSchema: {
            type: 'object',
            properties: {
              url: {
                type: 'string',
                description: '包含表单的网页URL',
              },
              form_selector: {
                type: 'string',
                description: '表单选择器',
                default: 'form',
              },
              test_data: {
                type: 'object',
                description: '测试数据键值对',
              },
            },
            required: ['url'],
          },
        },
        {
          name: 'extract_errors',
          description: '提取网页中的错误信息',
          inputSchema: {
            type: 'object',
            properties: {
              url: {
                type: 'string',
                description: '要检查的网页URL',
              },
            },
            required: ['url'],
          },
        },
      ],
    }));

    // 处理工具调用
    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;

      try {
        switch (name) {
          case 'visit_webpage':
            return await this.visitWebpage(args);
          case 'analyze_page_content':
            return await this.analyzePageContent(args);
          case 'check_api_endpoints':
            return await this.checkApiEndpoints(args);
          case 'test_form_interaction':
            return await this.testFormInteraction(args);
          case 'extract_errors':
            return await this.extractErrors(args);
          default:
            throw new Error(`Unknown tool: ${name}`);
        }
      } catch (error) {
        return {
          content: [
            {
              type: 'text',
              text: `Error: ${error.message}`,
            },
          ],
        };
      }
    });
  }

  async ensureBrowser() {
    if (!this.browser) {
      this.browser = await puppeteer.launch({
        headless: 'new',
        args: ['--no-sandbox', '--disable-setuid-sandbox'],
      });
    }
    return this.browser;
  }

  async visitWebpage(args) {
    const { url, wait_for_selector, screenshot = false } = args;
    
    const browser = await this.ensureBrowser();
    const page = await browser.newPage();
    
    try {
      // 设置用户代理
      await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36');
      
      // 访问页面
      await page.goto(url, { waitUntil: 'networkidle2', timeout: 30000 });
      
      // 等待特定元素（如果指定）
      if (wait_for_selector) {
        await page.waitForSelector(wait_for_selector, { timeout: 10000 });
      }
      
      // 获取页面内容
      const content = await page.content();
      const title = await page.title();
      const currentUrl = page.url();
      
      // 提取关键信息
      const $ = cheerio.load(content);
      const textContent = $('body').text().substring(0, 5000); // 限制长度
      
      let result = {
        url: currentUrl,
        title,
        textContent,
        htmlLength: content.length,
        timestamp: new Date().toISOString(),
      };
      
      // 截图（如果需要）
      if (screenshot) {
        const screenshotBuffer = await page.screenshot({ fullPage: true });
        result.screenshot = screenshotBuffer.toString('base64');
      }
      
      return {
        content: [
          {
            type: 'text',
            text: `✅ 成功访问网页: ${url}

📊 页面信息:
- 标题: ${title}
- 当前URL: ${currentUrl}
- HTML长度: ${content.length} 字符
- 访问时间: ${result.timestamp}

📝 页面内容预览:
${textContent.substring(0, 1000)}${textContent.length > 1000 ? '...' : ''}

${screenshot ? '📸 页面截图已生成' : ''}`,
          },
        ],
      };
      
    } finally {
      await page.close();
    }
  }

  async analyzePageContent(args) {
    const { url, focus_area } = args;
    
    const browser = await this.ensureBrowser();
    const page = await browser.newPage();
    
    try {
      await page.goto(url, { waitUntil: 'networkidle2' });
      
      // 分析页面结构
      const analysis = await page.evaluate((focusArea) => {
        const result = {
          forms: [],
          buttons: [],
          inputs: [],
          links: [],
          apis: [],
          errors: [],
          structure: {},
        };
        
        // 分析表单
        document.querySelectorAll('form').forEach((form, index) => {
          const formData = {
            index,
            action: form.action,
            method: form.method,
            inputs: [],
          };
          
          form.querySelectorAll('input, select, textarea').forEach(input => {
            formData.inputs.push({
              type: input.type,
              name: input.name,
              id: input.id,
              placeholder: input.placeholder,
              required: input.required,
            });
          });
          
          result.forms.push(formData);
        });
        
        // 分析按钮
        document.querySelectorAll('button, input[type="button"], input[type="submit"]').forEach(btn => {
          result.buttons.push({
            text: btn.textContent || btn.value,
            type: btn.type,
            id: btn.id,
            className: btn.className,
            onclick: btn.onclick ? 'Has click handler' : 'No click handler',
          });
        });
        
        // 分析链接
        document.querySelectorAll('a[href]').forEach(link => {
          if (link.href.includes('/api/')) {
            result.apis.push({
              href: link.href,
              text: link.textContent,
            });
          } else {
            result.links.push({
              href: link.href,
              text: link.textContent.substring(0, 50),
            });
          }
        });
        
        // 检查错误信息
        const errorSelectors = ['.error', '.alert-danger', '.notification.error', '[class*="error"]'];
        errorSelectors.forEach(selector => {
          document.querySelectorAll(selector).forEach(el => {
            if (el.textContent.trim()) {
              result.errors.push({
                selector,
                text: el.textContent.trim(),
                visible: el.offsetParent !== null,
              });
            }
          });
        });
        
        // 页面结构分析
        result.structure = {
          totalElements: document.querySelectorAll('*').length,
          scripts: document.querySelectorAll('script').length,
          stylesheets: document.querySelectorAll('link[rel="stylesheet"]').length,
          images: document.querySelectorAll('img').length,
          hasJQuery: typeof window.jQuery !== 'undefined',
          hasReact: typeof window.React !== 'undefined',
          hasVue: typeof window.Vue !== 'undefined',
        };
        
        return result;
      }, focus_area);
      
      return {
        content: [
          {
            type: 'text',
            text: `🔍 页面内容分析报告: ${url}

📋 表单分析 (${analysis.forms.length} 个):
${analysis.forms.map((form, i) => `
  表单 ${i + 1}:
  - Action: ${form.action || '未设置'}
  - Method: ${form.method || 'GET'}
  - 输入字段: ${form.inputs.length} 个
    ${form.inputs.map(input => `    • ${input.name || input.id}: ${input.type}`).join('\n')}
`).join('')}

🔘 按钮分析 (${analysis.buttons.length} 个):
${analysis.buttons.slice(0, 10).map(btn => `  • "${btn.text}" (${btn.type}) - ${btn.onclick}`).join('\n')}

🔗 API 端点 (${analysis.apis.length} 个):
${analysis.apis.map(api => `  • ${api.href}`).join('\n')}

❌ 错误信息 (${analysis.errors.length} 个):
${analysis.errors.map(error => `  • ${error.text} (${error.visible ? '可见' : '隐藏'})`).join('\n')}

🏗️ 页面结构:
- 总元素数: ${analysis.structure.totalElements}
- JavaScript 文件: ${analysis.structure.scripts}
- CSS 文件: ${analysis.structure.stylesheets}
- 图片: ${analysis.structure.images}
- 前端框架: ${analysis.structure.hasReact ? 'React' : analysis.structure.hasVue ? 'Vue' : analysis.structure.hasJQuery ? 'jQuery' : '原生JS'}

${focus_area ? `\n🎯 重点分析区域: ${focus_area}` : ''}`,
          },
        ],
      };
      
    } finally {
      await page.close();
    }
  }

  async checkApiEndpoints(args) {
    const { url, duration = 10 } = args;
    
    const browser = await this.ensureBrowser();
    const page = await browser.newPage();
    
    const networkRequests = [];
    
    try {
      // 监听网络请求
      page.on('request', request => {
        networkRequests.push({
          url: request.url(),
          method: request.method(),
          headers: request.headers(),
          postData: request.postData(),
          timestamp: Date.now(),
        });
      });
      
      page.on('response', response => {
        const request = networkRequests.find(req => req.url === response.url());
        if (request) {
          request.status = response.status();
          request.statusText = response.statusText();
          request.responseHeaders = response.headers();
        }
      });
      
      await page.goto(url, { waitUntil: 'networkidle2' });
      
      // 等待指定时间以捕获更多请求
      await new Promise(resolve => setTimeout(resolve, duration * 1000));
      
      // 尝试触发一些交互
      await page.evaluate(() => {
        // 点击一些按钮
        document.querySelectorAll('button, [data-action]').forEach((btn, i) => {
          if (i < 3) { // 只点击前3个
            try {
              btn.click();
            } catch (e) {
              console.log('Click failed:', e);
            }
          }
        });
      });
      
      // 再等待一段时间
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // 分析API请求
      const apiRequests = networkRequests.filter(req => 
        req.url.includes('/api/') || 
        req.method !== 'GET' ||
        req.url.includes('json')
      );
      
      return {
        content: [
          {
            type: 'text',
            text: `🌐 网络请求分析报告: ${url}

📊 总请求数: ${networkRequests.length}
🔗 API 请求数: ${apiRequests.length}

🔍 API 端点详情:
${apiRequests.map(req => `
  ${req.method} ${req.url}
  状态: ${req.status || '待响应'} ${req.statusText || ''}
  时间: ${new Date(req.timestamp).toLocaleTimeString()}
  ${req.postData ? `数据: ${req.postData.substring(0, 200)}` : ''}
`).join('\n')}

📈 请求统计:
- GET: ${networkRequests.filter(r => r.method === 'GET').length}
- POST: ${networkRequests.filter(r => r.method === 'POST').length}
- PUT: ${networkRequests.filter(r => r.method === 'PUT').length}
- DELETE: ${networkRequests.filter(r => r.method === 'DELETE').length}

❌ 失败请求:
${networkRequests.filter(r => r.status >= 400).map(r => `  • ${r.method} ${r.url} - ${r.status}`).join('\n')}`,
          },
        ],
      };
      
    } finally {
      await page.close();
    }
  }

  async testFormInteraction(args) {
    const { url, form_selector = 'form', test_data = {} } = args;
    
    const browser = await this.ensureBrowser();
    const page = await browser.newPage();
    
    try {
      await page.goto(url, { waitUntil: 'networkidle2' });
      
      const results = await page.evaluate((selector, data) => {
        const form = document.querySelector(selector);
        if (!form) {
          return { error: '未找到指定表单' };
        }
        
        const results = {
          formFound: true,
          interactions: [],
          errors: [],
        };
        
        // 填充表单数据
        Object.entries(data).forEach(([key, value]) => {
          const input = form.querySelector(`[name="${key}"], #${key}`);
          if (input) {
            try {
              input.value = value;
              input.dispatchEvent(new Event('input', { bubbles: true }));
              results.interactions.push(`✅ 设置 ${key} = ${value}`);
            } catch (e) {
              results.errors.push(`❌ 无法设置 ${key}: ${e.message}`);
            }
          } else {
            results.errors.push(`❌ 未找到字段: ${key}`);
          }
        });
        
        return results;
      }, form_selector, test_data);
      
      return {
        content: [
          {
            type: 'text',
            text: `🧪 表单交互测试报告: ${url}

${results.error ? `❌ ${results.error}` : `✅ 表单测试完成

📝 交互记录:
${results.interactions.join('\n')}

${results.errors.length > 0 ? `\n❌ 错误记录:\n${results.errors.join('\n')}` : ''}`}`,
          },
        ],
      };
      
    } finally {
      await page.close();
    }
  }

  async extractErrors(args) {
    const { url } = args;
    
    const browser = await this.ensureBrowser();
    const page = await browser.newPage();
    
    const consoleErrors = [];
    const networkErrors = [];
    
    try {
      // 监听控制台错误
      page.on('console', msg => {
        if (msg.type() === 'error') {
          consoleErrors.push({
            text: msg.text(),
            location: msg.location(),
            timestamp: Date.now(),
          });
        }
      });
      
      // 监听网络错误
      page.on('requestfailed', request => {
        networkErrors.push({
          url: request.url(),
          failure: request.failure().errorText,
          method: request.method(),
        });
      });
      
      await page.goto(url, { waitUntil: 'networkidle2' });
      
      // 提取页面中的错误元素
      const pageErrors = await page.evaluate(() => {
        const errors = [];
        const errorSelectors = [
          '.error', '.alert-danger', '.notification.error', 
          '[class*="error"]', '.text-danger', '.has-error'
        ];
        
        errorSelectors.forEach(selector => {
          document.querySelectorAll(selector).forEach(el => {
            if (el.textContent.trim() && el.offsetParent !== null) {
              errors.push({
                selector,
                text: el.textContent.trim(),
                className: el.className,
              });
            }
          });
        });
        
        return errors;
      });
      
      return {
        content: [
          {
            type: 'text',
            text: `🚨 错误提取报告: ${url}

📱 页面错误元素 (${pageErrors.length} 个):
${pageErrors.map(error => `  • ${error.text} (${error.selector})`).join('\n')}

🖥️ 控制台错误 (${consoleErrors.length} 个):
${consoleErrors.map(error => `  • ${error.text} - ${error.location?.url}:${error.location?.lineNumber}`).join('\n')}

🌐 网络错误 (${networkErrors.length} 个):
${networkErrors.map(error => `  • ${error.method} ${error.url} - ${error.failure}`).join('\n')}

${pageErrors.length === 0 && consoleErrors.length === 0 && networkErrors.length === 0 ? '✅ 未检测到明显错误' : ''}`,
          },
        ],
      };
      
    } finally {
      await page.close();
    }
  }

  async cleanup() {
    if (this.browser) {
      await this.browser.close();
    }
  }

  async run() {
    const transport = new StdioServerTransport();
    await this.server.connect(transport);
    console.error('AI Chat Plugin Browser MCP Server running on stdio');
  }
}

const server = new BrowserServer();
server.run().catch(console.error);
