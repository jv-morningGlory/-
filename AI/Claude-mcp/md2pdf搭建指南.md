# md2pdf MCP 服务

> 将 Markdown 转为 PDF，支持中文、代码高亮、表格。参照此文档搭建一个完全一样的 MCP 服务。

## .mcp.json（项目根目录）

```json
{
  "mcpServers": {
    "md2pdf": {
      "command": "node",
      "args": [".mcp-servers/md2pdf/index.mjs"]
    }
  }
}
```

## package.json（.mcp-servers/md2pdf/）

```json
{
  "name": "md2pdf",
  "version": "1.0.0",
  "main": "index.mjs",
  "type": "module",
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.29.0",
    "md-to-pdf": "^5.2.5"
  }
}
```

## index.mjs（.mcp-servers/md2pdf/）

```javascript
#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { readFileSync, writeFileSync, existsSync } from 'fs';
import { resolve, basename } from 'path';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const { marked } = require('marked');
const puppeteer = (await import('puppeteer')).default;

const DEFAULT_CSS = `
body {
  font-family: "Microsoft YaHei", "SimHei", "SimSun", sans-serif;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}
h1, h2, h3, h4, h5, h6 {
  font-family: "Microsoft YaHei", "SimHei", sans-serif;
  color: #1a1a1a;
  margin-top: 1.5em;
  margin-bottom: 0.8em;
}
code {
  font-family: "Cascadia Code", "Fira Code", "Consolas", monospace;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}
pre {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
}
pre code {
  background: none;
  padding: 0;
}
blockquote {
  border-left: 4px solid #ddd;
  margin: 1em 0;
  padding: 0.5em 1em;
  color: #666;
}
table {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}
th, td {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: left;
}
th {
  background: #f5f5f5;
}
hr {
  border: none;
  border-top: 1px solid #ddd;
  margin: 2em 0;
}
`;

const server = new Server(
  { name: 'md2pdf', version: '2.0.0' },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [{
    name: 'md2pdf',
    description: '将 Markdown 文件转换为 PDF。支持中文渲染、代码高亮、表格等。自动处理中文字体。',
    inputSchema: {
      type: 'object',
      properties: {
        path: { type: 'string', description: 'Markdown 文件的绝对路径或相对路径' },
        output: { type: 'string', description: '输出 PDF 文件路径。默认与源文件同目录，扩展名改为 .pdf' },
        css: { type: 'string', description: '自定义 CSS 样式，会追加到默认样式之后。' },
      },
      required: ['path'],
    },
  }],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name !== 'md2pdf') throw new Error(`未知工具: ${request.params.name}`);

  const args = request.params.arguments || {};
  const inputPath = resolve(args.path);
  const outputPath = args.output ? resolve(args.output) : inputPath.replace(/\.md$/i, '.pdf');

  if (!existsSync(inputPath)) {
    return { content: [{ type: 'text', text: `错误：文件不存在: ${inputPath}` }], isError: true };
  }
  if (!inputPath.toLowerCase().endsWith('.md')) {
    return { content: [{ type: 'text', text: `错误：仅支持 .md 文件` }], isError: true };
  }

  try {
    const mdContent = readFileSync(inputPath, 'utf-8');
    const htmlBody = marked(mdContent);
    const fullHtml = `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>${DEFAULT_CSS}${args.css || ''}</style></head><body>${htmlBody}</body></html>`;

    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();
    await page.setContent(fullHtml, { waitUntil: 'networkidle0', timeout: 60000 });
    const pdfBuffer = await page.pdf({
      format: 'A4',
      margin: { top: '20mm', bottom: '20mm', left: '15mm', right: '15mm' },
      printBackground: true,
    });
    await page.close();
    await browser.close();

    if (!pdfBuffer || pdfBuffer.length === 0) {
      return { content: [{ type: 'text', text: '错误：PDF 生成失败' }], isError: true };
    }

    writeFileSync(outputPath, pdfBuffer);
    return {
      content: [{
        type: 'text',
        text: `PDF 生成成功！\n文件路径: ${outputPath}\n文件大小: ${(pdfBuffer.length / 1024).toFixed(1)} KB`,
      }],
    };
  } catch (err) {
    return { content: [{ type: 'text', text: `转换失败: ${err.message}` }], isError: true };
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
```

## 搭建命令

```bash
mkdir -p .mcp-servers/md2pdf
# 将上面的 package.json 和 index.mjs 放入 .mcp-servers/md2pdf/
# 将 .mcp.json 放到项目根目录
cd .mcp-servers/md2pdf && npm install
```
