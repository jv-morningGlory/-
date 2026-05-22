from PIL import Image, ImageDraw, ImageFont
import os

# Canvas
W, H = 860, 1220
img = Image.new("RGB", (W, H), "#FAFAFA")
draw = ImageDraw.Draw(img)

# ── Try to load a CJK font ──────────────────────────────
def get_font(size, bold=False):
    """Fallback chain for Windows CJK fonts"""
    candidates = [
        "C:\\Windows\\Fonts\\msyh.ttc",       # 微软雅黑
        "C:\\Windows\\Fonts\\msyhbd.ttc",     # 微软雅黑 Bold
        "C:\\Windows\\Fonts\\simhei.ttf",     # 黑体
        "C:\\Windows\\Fonts\\simsun.ttc",     # 宋体
        "C:\\Windows\\Fonts\\simkai.ttf",     # 楷体
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()

F  = get_font   # shorthand

# ── Color palette ────────────────────────────────────────
BG      = "#FAFAFA"
WHITE   = "#FFFFFF"
BORDER  = "#333333"
ACCENT  = "#1A73E8"   # blue
ORANGE  = "#E8710A"
GREEN   = "#188038"
PURPLE  = "#9334E6"
RED     = "#D93025"
GRAY_BG = "#F1F3F4"
LIGHT_BLUE = "#E8F0FE"

def roundrect(d, xy, r, fill, outline, width=2):
    """Draw a rounded rectangle."""
    x1, y1, x2, y2 = xy
    d.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)

def section(d, x, y, w, h, title, subtitle, color, fields=None):
    """Draw a section box with title + optional field rows."""
    roundrect(d, (x, y, x+w, y+h), r=6, fill=WHITE, outline=color, width=2)

    # Title bar
    d.rectangle((x+2, y+2, x+w-2, y+34), fill=color)
    d.text((x+14, y+7), title, fill="white", font=F(15))
    if subtitle:
        sw = F(13).getbbox(subtitle)[2]
        d.text((x+w-sw-14, y+8), subtitle, fill=(255,255,255,180), font=F(13))

    # Fields
    if fields:
        fh = 22
        fy = y + 42
        for label, desc, val in fields:
            d.text((x+16, fy+1), label, fill="#202124", font=F(13))
            d.text((x+230, fy+1), desc, fill="#5F6368", font=F(12))
            if val:
                vw = F(12).getbbox(val)[2]
                d.text((x+w-vw-16, fy+1), val, fill=color, font=F(12))
            fy += fh

def arrow_right(d, cx, cy):
    d.polygon([(cx-5,cy-4), (cx+5,cy), (cx-5,cy+4)], fill=ACCENT)

def arrow_down(d, cx, cy):
    d.polygon([(cx-4,cy-5), (cx+4,cy-5), (cx,cy+5)], fill=ACCENT)

# ── Layout constants ─────────────────────────────────────
LX = 40          # left margin
RX = W - 40      # right margin
CW = RX - LX     # content width
GAP = 14         # vertical gap between sections

# ── TITLE ─────────────────────────────────────────────────
title_y = 20
draw.text((LX, title_y), "InnoDB 数据页结构", fill="#202124", font=F(26))
draw.text((LX, title_y+38), "默认大小 16KB ｜ 页是 InnoDB 磁盘与内存交互的最小单位", fill="#5F6368", font=F(14))

# ── Helper to stack sections ──────────────────────────────
cy = title_y + 68

# ── 1. File Header ────────────────────────────────────────
fh = 140
section(draw, LX, cy, CW, fh,
    "File Header (文件头)", "38 字节", ACCENT,
    [
        ("FIL_PAGE_SPACE",      "表空间 ID",        "4B"),
        ("FIL_PAGE_OFFSET",     "当前页号",          "4B"),
        ("FIL_PAGE_PREV",       "上一页指针 → 双向链表", "4B"),
        ("FIL_PAGE_NEXT",       "下一页指针 → 双向链表", "4B"),
    ])
cy += fh + GAP

# ── 2. Page Header ────────────────────────────────────────
ph = 168
section(draw, LX, cy, CW, ph,
    "Page Header (页头)", "56 字节", GREEN,
    [
        ("PAGE_N_DIR_SLOTS",    "Page Directory 槽数量",       "2B"),
        ("PAGE_HEAP_TOP",       "空闲空间起始偏移量",           "2B"),
        ("PAGE_N_HEAP",         "记录总数 (含已标记删除)",       "2B"),
        ("PAGE_FREE",           "可重用空闲链表头指针",          "2B"),
        ("PAGE_GARBAGE",        "已删除记录总字节数",           "2B"),
        ("PAGE_LAST_INSERT",    "最后插入记录的位置",           "2B"),
    ])
cy += ph + GAP

# ── 3. Infimum + Supremum ─────────────────────────────────
ish = 62
section(draw, LX, cy, CW, ish,
    "Infimum + Supremum (伪记录)", "", PURPLE,
    [
        ("Infimum",  "最小虚拟记录 (比任何主键都小)",   ""),
        ("Supremum", "最大虚拟记录 (比任何主键都大)",   ""),
    ])
cy += ish + GAP

# ── 4. User Records ───────────────────────────────────────
urh = 160
x, y, w = LX, cy, CW
roundrect(draw, (x, y, x+w, y+urh), r=6, fill=WHITE, outline=ORANGE, width=2)
draw.rectangle((x+2, y+2, x+w-2, y+34), fill=ORANGE)
draw.text((x+14, y+7), "User Records (用户记录)", fill="white", font=F(15))
draw.text((x+w-160, y+8), "单向链表 · 按主键升序", fill=(255,255,255,180), font=F(13))

# Row boxes
row_w, row_h = 110, 42
row_y = y + 50
gap_x = 36
start_x = x + 30
colors = ["#E8F0FE", "#FCE8E6", "#E6F4EA", "#FEF7E0", "#F3E8FD"]
labels = ["Row 1\n(id=5)", "Row 2\n(id=12)", "Row 3\n(id=23)", "Row 4\n(id=38)", "..."]
for i in range(5):
    rx = start_x + i * (row_w + gap_x)
    roundrect(draw, (rx, row_y, rx+row_w, row_y+row_h), r=5, fill=colors[i], outline=ORANGE, width=1)
    # Centered text
    lines = labels[i].split("\n")
    draw.text((rx+row_w//2 - F(13).getbbox(lines[0])[2]//2, row_y+6), lines[0], fill="#202124", font=F(13))
    if len(lines) > 1:
        draw.text((rx+row_w//2 - F(12).getbbox(lines[1])[2]//2, row_y+24), lines[1], fill="#5F6368", font=F(12))
    # Arrow between boxes
    if i < 4:
        ax = rx + row_w + 4
        ay = row_y + row_h//2
        draw.line((ax, ay, ax+gap_x-8, ay), fill=ORANGE, width=2)
        arrow_right(draw, ax+gap_x-8, ay)

# Annotation below
draw.text((x+30, row_y+row_h+14), "记录格式:  | 额外信息(变长字段长度列表 · NULL 位图 · 记录头信息 5B) | 列1 | 列2 | ... | 主键 | 事务ID | 回滚指针 |",
          fill="#5F6368", font=F(11))

cy += urh + GAP

# ── 5. Free Space ─────────────────────────────────────────
fsh = 64
section(draw, LX, cy, CW, fsh,
    "Free Space (空闲空间)", "", "#80868B",
    [
        ("动态区域", "User Records 向下增长 ⟷ Page Directory 向上增长", ""),
    ])

# Draw growth arrows inside free space
mx = LX + CW//2
draw.line((mx-60, cy+48, mx+60, cy+48), fill="#80868B", width=1)
draw.text((mx-F(12).getbbox("← 记录向下 · 目录向上 →")[2]//2, cy+52),
          "← 记录向下 · 目录向上 →", fill="#80868B", font=F(12))

cy += fsh + GAP

# ── 6. Page Directory ─────────────────────────────────────
pdh = 150
section(draw, LX, cy, CW, pdh,
    "Page Directory (页目录)", "每个 Slot 2 字节 · 每组 4~8 条记录", RED,
    [])

# Slot visualization
slot_y = cy + 48
slot_w, slot_h = 92, 56
slot_gap = 24
n_slots = 5
slot_start_x = LX + (CW - (n_slots*slot_w + (n_slots-1)*slot_gap)) // 2
slot_labels = ["Slot 0", "Slot 1", "Slot 2", "Slot 3", "Slot 4"]
slot_targets = ["→ Infimum", "→ Row 2", "→ Row 4", "→ Row 6", "→ Supremum"]
slot_colors = ["#FCE8E6", "#FEF7E0", "#E8F0FE", "#E6F4EA", "#F3E8FD"]

for i in range(n_slots):
    sx = slot_start_x + i * (slot_w + slot_gap)
    roundrect(draw, (sx, slot_y, sx+slot_w, slot_y+slot_h), r=6, fill=slot_colors[i], outline=RED, width=2)
    draw.text((sx+slot_w//2 - F(14).getbbox(slot_labels[i])[2]//2, slot_y+6), slot_labels[i], fill="#202124", font=F(14))
    draw.text((sx+slot_w//2 - F(11).getbbox(slot_targets[i])[2]//2, slot_y+32), slot_targets[i], fill=RED, font=F(11))

draw.text((LX+CW//2 - F(12).getbbox("二分查找定位目标 Slot → 组内单向链表遍历 (≤8条) → 命中记录")[2]//2, slot_y+slot_h+12),
          "二分查找定位目标 Slot → 组内单向链表遍历 (≤8条) → 命中记录", fill="#5F6368", font=F(12))

cy += pdh + GAP

# ── 7. File Trailer ───────────────────────────────────────
th = 90
section(draw, LX, cy, CW, th,
    "File Trailer (文件尾)", "8 字节", ACCENT,
    [
        ("前 4 字节",  "页校验和 (Checksum)",      ""),
        ("后 4 字节",  "LSN 低 4 位 (完整性校验)",  ""),
    ])

# Footer note
foot_y = cy + th + 26
draw.text((LX, foot_y), "▸ 页从磁盘读取后，计算校验和与 File Trailer 中的值对比，不匹配则说明页损坏",
          fill="#80868B", font=F(12))

# ── Save ──────────────────────────────────────────────────
out = os.path.expanduser("c:/Users/heyoufeng/Desktop/随笔/中间件/mysql/数据页结构图.png")
img.save(out)
print(f"Saved → {out}")