#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { readFileSync, writeFileSync, existsSync, rmSync } from 'fs';
import { resolve, dirname, extname, basename } from 'path';
import { createRequire } from 'module';

// md-to-pdf 的依赖里自带了 marked，直接用
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
  tools: [
    {
      name: 'md2pdf',
      description: '将 Markdown 文件转换为 PDF。支持中文渲染、代码高亮、表格等。自动处理中文字体。',
      inputSchema: {
        type: 'object',
        properties: {
          path: {
            type: 'string',
            description: 'Markdown 文件的绝对路径或相对路径',
          },
          output: {
            type: 'string',
            description: '输出 PDF 文件路径。默认与源文件同目录，扩展名改为 .pdf',
          },
          css: {
            type: 'string',
            description: '自定义 CSS 样式，会追加到默认样式之后。默认已包含中字体和代码高亮样式。',
          },
        },
        required: ['path'],
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name !== 'md2pdf') {
    throw new Error(`未知工具: ${request.params.name}`);
  }

  const args = request.params.arguments || {};
  const inputPath = resolve(args.path);
  const outputPath = args.output ? resolve(args.output) : inputPath.replace(/\.md$/i, '.pdf');
  const customCss = args.css || '';

  if (!existsSync(inputPath)) {
    return {
      content: [{ type: 'text', text: `错误：文件不存在: ${inputPath}` }],
      isError: true,
    };
  }

  if (!inputPath.toLowerCase().endsWith('.md')) {
    return {
      content: [{ type: 'text', text: `错误：仅支持 .md 文件，当前文件: ${basename(inputPath)}` }],
      isError: true,
    };
  }

  try {
    const mdContent = readFileSync(inputPath, 'utf-8');
    const htmlBody = marked(mdContent);
    const fullHtml = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>${DEFAULT_CSS}${customCss}</style>
</head>
<body>${htmlBody}</body>
</html>`;

    // 写入临时 HTML 文件到 md 同目录，使相对路径图片能通过 file:// 协议正确加载
    const tmpHtmlPath = inputPath.replace(/\.md$/i, '.tmp.html');
    writeFileSync(tmpHtmlPath, fullHtml, 'utf-8');

    const browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();
    // 用 goto file:// 而非 setContent，确保页面 origin 为 file://，从而能加载相对路径图片
    const fileUrl = `file:///${encodeURI(tmpHtmlPath.replace(/\\/g, '/'))}`;
    await page.goto(fileUrl, { waitUntil: 'networkidle0', timeout: 60000 });
    const pdfBuffer = await page.pdf({
      format: 'A4',
      margin: { top: '20mm', bottom: '20mm', left: '15mm', right: '15mm' },
      printBackground: true,
    });
    await page.close();
    await browser.close();

    // 清理临时 HTML
    try { rmSync(tmpHtmlPath); } catch (_) {}

    if (!pdfBuffer || pdfBuffer.length === 0) {
      return {
        content: [{ type: 'text', text: '错误：PDF 生成失败，未返回内容' }],
        isError: true,
      };
    }

    writeFileSync(outputPath, pdfBuffer);

    return {
      content: [{
        type: 'text',
        text: `PDF 生成成功！\n文件路径: ${outputPath}\n文件大小: ${(pdfBuffer.length / 1024).toFixed(1)} KB`,
      }],
    };
  } catch (err) {
    return {
      content: [{ type: 'text', text: `转换失败: ${err.message}` }],
      isError: true,
    };
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
