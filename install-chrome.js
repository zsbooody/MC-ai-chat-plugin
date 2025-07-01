import puppeteer from 'puppeteer';

console.log('🚀 开始安装 Chrome 浏览器...');

try {
  const browser = await puppeteer.launch({
    headless: 'new'
  });
  
  console.log('✅ Chrome 浏览器安装成功！');
  console.log('📍 Chrome 路径:', browser.process()?.spawnfile || '系统默认路径');
  
  await browser.close();
  console.log('🎉 MCP Browser Server 准备就绪！');
  
} catch (error) {
  console.error('❌ Chrome 安装失败:', error.message);
  console.log('💡 请尝试手动安装：');
  console.log('   npm install puppeteer');
  console.log('   或者设置环境变量 PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true');
  process.exit(1);
} 