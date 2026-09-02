# 从 BST 到手写四种树（Java）

> 目标：能自己写出 **BST**、**AVL**、**红黑树**、**B+ Tree**。
> 四棵树是一条改造链，不是四套无关知识。配套可运行代码在 [`code/`](code/)。

---

## 一、先看路线图

| 树 | 相对上一棵多了什么 | 解决什么 | 写完怎么验 | Java / 生产里在哪 |
| --- | --- | --- | --- | --- |
| **BST** | `val + left + right`，左小右大 | 有序查找，中序有序 | 中序是排序结果 | 自己写 |
| **AVL** | 每个节点记 `height`，回溯时 `rebalance` | 严格平衡，查找稳在 O(log n) | 连续插 `10,20,30` 根必须是 20 | 考试常见 |
| **红黑树** | 每个节点记颜色，插入涂红再修 | 近似平衡，改动少（常只变色） | 根黑、无红红、黑高相同 | `TreeMap` / `TreeSet` / `HashMap` 树化 |
| **B+ Tree** | 多路节点 + 叶子链表 | 磁盘一次读一页；范围扫描走链表 | 插 1..20 后 `range(5,12)` 能扫出来 | InnoDB 索引 |

BST 只保证左小右大。连续插入有序序列会退化成一条链，查找从 O(log n) 变成 O(n)：

```text
插 10, 20, 30（普通 BST）

10                  10
  \                   \
  20        →         20
                        \
                        30     ← 已经是链表
```

后面三棵都在修这件事，手法不同：

- AVL：高度差不能超过 1，歪了立刻旋
- 红黑树：用颜色把「最长 < 2 × 最短」卡住，允许略歪，换旋转更少
- B+ Tree：不再是二叉。一个节点塞一页的 key，树很矮；叶子串成链表，范围查询不用再中序绕

> 动手顺序：先把 BST 的增删查写对（尤其删除），再给节点加字段往上改。不要一上来写红黑树。

---

## 二、BST

### 2.1 是什么

**二叉搜索树**（Binary Search Tree，也叫查询二叉树）：每个节点最多两个孩子，且对任意节点：

```text
左子树所有值  <  当前节点值  <  右子树所有值
```

中序遍历（左 → 根 → 右）得到有序序列。查找时小走左、大走右，不必扫整棵树。

```text
        5
       / \
      3   7
     / \ / \
    2  4 6  8

中序：2 3 4 5 6 7 8
```

普通二叉树左右随便挂；BST **必须左小右大**。

### 2.2 节点

```java
private static class Node {
    int val;
    Node left;
    Node right;

    Node(int val) {
        this.val = val;
    }
}
```

树 = 一个 `root` 指针 + 若干 `Node`。不需要 `parent` 也能做增删查。

### 2.3 三个操作

| 操作 | 做法 |
| --- | --- |
| 查找 | 从 root 出发，小走左、大走右，相等即找到，走到 null 即没有 |
| 插入 | 同查找路径，找到空位挂上（相等通常不插） |
| 删除 | 0/1 孩子：返回孩子顶替；2 孩子：后继值替换 + 再删后继 |

删除是 BST 唯一的难点。删有两个孩子的 5：

```text
      5                 6                 6
     / \               / \               / \
    3   7     →       3   7     →       3   7
       / \               / \                 \
      6   8             6   8                 8
                   值改成 6            再删旧的 6
```

> 0/1 孩子：返回孩子顶替；2 孩子：后继值替换 + 再删后继。后继 = 右子树最小值（一直往左走）。

### 2.4 完整代码

递归插入/删除都返回「这棵子树的新根」，这样删根时 `root = deleteRec(root, v)` 能接上。

```java
public class BinarySearchTree {

    private Node root;

    private static class Node {
        int val;
        Node left;
        Node right;

        Node(int val) {
            this.val = val;
        }
    }

    public void insert(int value) {
        root = insertRec(root, value);
    }

    private Node insertRec(Node node, int value) {
        if (node == null) {
            return new Node(value);
        }
        if (value < node.val) {
            node.left = insertRec(node.left, value);
        } else if (value > node.val) {
            node.right = insertRec(node.right, value);
        }
        return node;
    }

    public boolean contains(int value) {
        Node cur = root;
        while (cur != null) {
            if (value == cur.val) {
                return true;
            }
            cur = value < cur.val ? cur.left : cur.right;
        }
        return false;
    }

    public void delete(int value) {
        root = deleteRec(root, value);
    }

    private Node deleteRec(Node node, int value) {
        if (node == null) {
            return null;
        }
        if (value < node.val) {
            node.left = deleteRec(node.left, value);
            return node;
        }
        if (value > node.val) {
            node.right = deleteRec(node.right, value);
            return node;
        }
        if (node.left == null) {
            return node.right;
        }
        if (node.right == null) {
            return node.left;
        }
        Node min = findMin(node.right);
        node.val = min.val;
        node.right = deleteRec(node.right, min.val);
        return node;
    }

    private Node findMin(Node node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    public void print() {
        printInOrder(root);
        System.out.println();
    }

    private void printInOrder(Node node) {
        if (node == null) {
            return;
        }
        printInOrder(node.left);
        System.out.print(node.val + " ");
        printInOrder(node.right);
    }

    public static void main(String[] args) {
        BinarySearchTree bst = new BinarySearchTree();
        for (int v : new int[]{5, 3, 7, 2, 4, 6, 8}) {
            bst.insert(v);
        }
        bst.print();     // 2 3 4 5 6 7 8
        bst.delete(5);
        bst.print();     // 2 3 4 6 7 8
    }
}
```

### 2.5 怎么验

| 你看到的 | 判断 | 动作 |
| --- | --- | --- |
| 插完中序不是有序 | BST 性质丢了 | 检查 `value < node.val` 走左，否则走右 |
| 删 5 之后中序还在、但少了 5 | 删除对了 | — |
| 连续插 `10,20,30` 根仍是 10 | BST **本来就会退化** | 这不是 bug，是后面 AVL 要修的 |

> 查找、中序打印、`findMin` 后面三棵树几乎原样复用。真正要改的是 Node 字段和 insert/delete 的收尾。

---

## 三、AVL = BST + height + rebalance

> 约定：**高度 h** = 空树 0、叶子 1；**平衡因子**（Balance Factor，bf）= h(左) − h(右)。

### 3.1 为什么要 AVL

BST 不管高度。AVL 在 BST 之上多一条硬约束：

> **任意节点**的左右子树高度差不超过 1，即 **|bf| ≤ 1**。

一旦插入/删除让某个节点 `|bf| = 2`，立刻旋转扳回来。上面那个 `10,20,30`，AVL 会在插 30 时左旋：

```text
    20
   /  \
  10   30     ← 高度回到 2，查找仍是 O(log n)
```

| | BST | AVL |
| --- | --- | --- |
| 左小右大 | 要 | 要（中序仍然有序） |
| 左右高度差 | 不管 | **每个节点 \|bf\| ≤ 1** |
| 插入/删除后 | 挂上就结束 | 沿路检查，不平衡就旋转 |
| 最坏查找 | O(n) | **O(log n)** |

### 3.2 怎么标 h 和 bf

公式只有这一条，必须从叶子往根标：

```text
h(节点) = 1 + max(h(左孩子), h(右孩子))
bf(节点) = h(左) − h(右)
```

| bf | 含义 | 动作 |
| --- | --- | --- |
| +1 / 0 / −1 | 平衡 | 不用动 |
| **+2** | 左子树比右子树高 2 | 这棵子树要旋转（最终右旋） |
| **−2** | 右子树比左子树高 2 | 这棵子树要旋转（最终左旋） |

> **+ 就是左高，− 就是右高。** 插入/删除后不要扫整棵树，只重算「被改节点 → 根」这一条路。路上**第一个 |bf| = 2** 的节点记为 **A**，先修它。

口诀：**叶子 1、空 0；h = 1 + 较高的孩子；bf = 左 − 右；谁先爆 2 修谁。**

### 3.3 LL / LR / RR / RL

命名：**第一个字母 = A 哪侧重，第二个字母 = 重侧孩子哪侧重。** 同向单旋，拐弯双旋。

| bf(A) | 重侧孩子的 bf | 类型 | 操作 |
| --- | --- | --- | --- |
| +2 左重 | +1（删除时也可能是 0） | **LL** | 对 A **右旋** 1 次 |
| +2 左重 | −1 | **LR** | 先对左孩子**左旋**，再对 A **右旋** |
| −2 右重 | −1（删除时也可能是 0） | **RR** | 对 A **左旋** 1 次 |
| −2 右重 | +1 | **RL** | 先对右孩子**右旋**，再对 A **左旋** |

**右旋**（LL 的那一下，也是 LR 的第二下）：

```text
      A                     B
     / \                   / \
    B   T4     →         C     A
   / \                       / \
  C   T3                   T3  T4
```

1. B = A.left，B 升为新根
2. **T3（B 的右子树）过继给 A 当左孩子**
3. A 降为 B 的右孩子

**左旋**（RR 的那一下，也是 RL 的第二下）：镜像，T2（B 的左子树）过继给 A 当右孩子。

四种插入：

```text
LL 插 30,20,10          RR 插 10,20,30

    30                    10                    都变成
   /                        \                     20
  20                         20                  /  \
 /                             \               10    30
10                              30
```

```text
LR 插 30,10,20          RL 插 10,30,20

    30                    10
   /                        \          拐弯必须先拧直，再单旋
  10                         30        最终根也是 20
    \                       /
    20                     20
```

> 不要直接对 LR 的 30 右旋，中序会乱。必须先把「弯」拧直。
> 孩子 bf = 0 **只出现在删除**：仍按单旋做。

现场 4 步：只看一条路 → 找最低失衡点 A → 看 `bf(A)` 和重侧孩子定类型 → 转完再往上（插入最多修 1 次；删除可能继续）。

### 3.4 从 BST 改成 AVL（动手）

AVL = BST + 每个节点记高度 + 插入/删除回溯时 `rebalance`。

查找、中序打印、`findMin` **一行都不用改**。只动 Node、insert、delete。

**第 1 步：Node 加 `height`**，叶子 = 1，空树 = 0。

```java
private static class Node {
    int val;
    int height = 1;
    Node left;
    Node right;

    Node(int val) {
        this.val = val;
    }
}
```

**第 2 步：三个工具**

```java
private int h(Node n) {
    return n == null ? 0 : n.height;
}

private void refresh(Node n) {
    n.height = 1 + Math.max(h(n.left), h(n.right));
}

private int bf(Node n) {
    return n == null ? 0 : h(n.left) - h(n.right);
}
```

**第 3 步：左旋 / 右旋**。先 `refresh` 降下去的，再 `refresh` 升上来的（高度依赖孩子）。

```java
private Node rotateRight(Node a) {
    Node b = a.left;
    a.left = b.right;  // T3 过继给 A
    b.right = a;
    refresh(a);
    refresh(b);
    return b;
}

private Node rotateLeft(Node a) {
    Node b = a.right;
    a.right = b.left;  // T2 过继给 A
    b.left = a;
    refresh(a);
    refresh(b);
    return b;
}
```

**第 4 步：`rebalance` 就是上面那张表**

```java
private Node rebalance(Node a) {
    refresh(a);
    if (bf(a) > 1) {              // 左重 → 最终右旋
        if (bf(a.left) < 0) {     // LR：先拧直
            a.left = rotateLeft(a.left);
        }
        return rotateRight(a);
    }
    if (bf(a) < -1) {             // 右重 → 最终左旋
        if (bf(a.right) > 0) {    // RL：先拧直
            a.right = rotateRight(a.right);
        }
        return rotateLeft(a);
    }
    return a;
}
```

**第 5 步：原来 `return node`，现在 `return rebalance(node)`**

递归从叶子往根返回，所以自然是「先修最低失衡点」。

```java
private Node insertRec(Node node, int value) {
    if (node == null) {
        return new Node(value);
    }
    if (value < node.val) {
        node.left = insertRec(node.left, value);
    } else if (value > node.val) {
        node.right = insertRec(node.right, value);
    }
    return rebalance(node);
}

private Node deleteRec(Node node, int value) {
    if (node == null) {
        return null;
    }
    if (value < node.val) {
        node.left = deleteRec(node.left, value);
    } else if (value > node.val) {
        node.right = deleteRec(node.right, value);
    } else {
        if (node.left == null) {
            return node.right;   // 已删，父节点回溯时再 rebalance
        }
        if (node.right == null) {
            return node.left;
        }
        Node min = findMin(node.right);
        node.val = min.val;
        node.right = deleteRec(node.right, min.val);
    }
    return rebalance(node);
}
```

> `5,3,7,2,4,6,8` 本来就平衡，看不出 AVL 和 BST 的差别。必须用连续递增 `10,20,30` 验：BST 根是 10，AVL 根必须是 20。

完整可运行代码：[`code/AvlTree.java`](code/AvlTree.java)。

判断转对了，只看三件事：

1. 中序仍然有序（BST 没丢）
2. 连续插 `10,20,30` 后根是 20，不是 10
3. 每个节点 `|bf| ≤ 1`

---

## 四、红黑树 = BST + 五条颜色规则

> 先会 BST 和 AVL 旋转。红黑树规则比 AVL 松：允许一边暂时更高，换来插入/删除经常只变色、少旋转。
>
> 图例：`20黑` / `10红`。空指针 **NIL** 算黑叶子，平时可不画，数黑高时必须算。

Java 里：`TreeMap` / `TreeSet` 底层就是它；`HashMap` 桶链表过长会树化成它。

### 4.1 五条规则

口诀：**根黑、叶黑、红不连红、每条路黑一样多。**

| 规则 | 对 | 错 |
| --- | --- | --- |
| 1. 节点非红即黑 | — | — |
| 2. 根必须黑 | `16黑` | `16红` |
| 3. NIL 算黑 | 空孩子都是黑叶子 | 数黑高时漏算 NIL |
| 4. 红不连红 | 红的父、左、右都是黑 | `8红` 下面再挂 `4红` |
| 5. 黑高相同 | 从某节点走到每个叶子，黑节点个数一样 | 左边 3 个黑、右边 2 个黑 |

**黑高**：从某节点走到 NIL，路上的黑节点个数（红当透明，路过不算）。

黑是骨架（计入黑高），红是夹在骨架缝里的额外一层（不计入）。所以：

- 新节点**一律涂红**（不增加黑高，最多破「红不连红」）
- 红不能挨红（挨了，最长路径就会超过最短的 2 倍）
- 变色 / 旋转：把红挪到合法位置，或把红收进骨架

> 先确认它是 BST，再查这五条：根是不是黑 → 有没有红红挨着 → 从某节点往下左右两条路数黑。

### 4.2 插入：出错 = 红红相邻

按 BST 找到空位，挂上，**涂红**。父是黑 → 结束。父也是红 → 修。

四个角色：`N` = 当前（一开始是新节点），`P` = 父，`G` = 祖父，`U` = 叔叔（P 的兄弟，没有就是 NIL，算黑）。

现场 3 问：

| 问 | 是 | 动作 |
| --- | --- | --- |
| 1. 父是黑？ | 是 | 结束 |
| 2. 叔叔是红？ | 是 | **只变色**：P、U 改黑，G 改红；N 挪到 G，回到第 1 问 |
| 3. 叔叔是黑？ | 是 | 看 N 相对 G：拐弯先拧直，同向旋祖父收工 |

同向 / 拐弯和 AVL 同一套旋转：LL 对 G 右旋，LR 先对 P 左旋再对 G 右旋，RR / RL 镜像。

```text
叔红 → 变色，不旋转              叔黑 + 同向 → 旋祖父 + 变色，结束

    G黑            G红               G黑            P黑
   /   \          /   \             /              /   \
 P红   U红  →   P黑   U黑         P红      →    N红   G红
 /              /                 /
N红            N红              N红
```

变色可能走到根；旋转最多 2 次。根最后必须刷黑。

> 手算只问三句：**父黑了没？叔叔红不红？同向还是拐弯？**

### 4.3 删除：出错 = 少了一个黑（双黑）

先按 BST 删。真正摘掉的节点最多 1 个孩子。

| 被摘掉的 | 黑高 | 措施 |
| --- | --- | --- |
| **红** | 没变 | 摘掉就结束 |
| **黑** | 那条路少 1 个黑 | 顶上来的节点带一个「额外的黑」，叫**双黑**，必须修 |

记：`x` = 双黑所在，`P` = 父，`S` = 兄弟。侄子是 S 的两个孩子：**近侄**朝向 x（内侧），**远侄**背向 x（外侧）。

现场 4 问：

| 条件 | 措施 |
| --- | --- |
| 兄弟是红 | 对 P 向 x 侧旋一次，S 改黑、P 改红。新兄弟变成黑，掉进下面几行 |
| 兄弟黑，两侄都黑 | S 改红，双黑上移到 P。P 原红 → 改黑后结束；P 原黑 → P 变新双黑，继续 |
| 兄弟黑，近侄红、远侄黑 | 对 S 旋一次（把近侄提上来），转成下一行 |
| 兄弟黑，远侄红 | 对 P 向 x 侧旋，S 继承 P 的颜色，P 改黑，远侄改黑。双黑消失，结束 |

> 口诀：**先把红兄弟转成黑兄弟；两侄都黑就把洞往上推；否则拧成远侄红，旋父收洞。**
> `x` 走到根：根刷成普通黑，那份额外的黑直接丢掉。

### 4.4 从 AVL 改成红黑树（动手）

和 AVL 的差别：

| | AVL | 红黑树（推荐写法） |
| --- | --- | --- |
| 平衡信息 | `height` | `color`（1 bit） |
| 空孩子 | `null` | **NIL 哨兵**（黑色，所有空指针共用） |
| 旋转 | 只改 left/right，递归返回新根 | 还要改 **parent**（fixup 是 while 循环，要能找到叔/兄） |
| 插入收尾 | `return rebalance(node)` | 新节点涂红，再 `insertFixup` |
| 删除收尾 | 同样 `rebalance` | 被摘的是黑才 `deleteFixup` |

Node 比 BST 多两个字段：

```java
private static final boolean RED = true;
private static final boolean BLACK = false;

private static class Node {
    int val;
    boolean color;
    Node left, right, parent;
}
```

构造时先做 NIL，所有空孩子都指向它。`root.parent = NIL`。旋转必须把 parent 一起改，否则 fixup 找不到祖父。

`insertFixup` 就是 4.2 的三问，`deleteFixup` 就是 4.3 的四问。完整可运行代码（含五条规则校验）：[`code/RedBlackTree.java`](code/RedBlackTree.java)。

对照代码时按这个地图看：

| 笔记里的话 | 代码里的位置 |
| --- | --- |
| 新节点涂红 | `new Node(value, RED)` |
| 父黑则结束 | `while (z.parent.color == RED)` |
| 叔红只变色 | `uncle.color == RED` 那个分支 |
| 拐弯先拧直 | `z == z.parent.right` 时先 `rotateLeft(z.parent)` |
| 同向旋祖父 | `rotateRight(z.parent.parent)` + 变色 |
| 根必须黑 | 循环结束后 `root.color = BLACK` |
| 双黑 | 被摘节点原色是黑，才进 `deleteFixup` |
| 兄弟红 | `w.color == RED` |
| 两侄都黑 | `w.left` 和 `w.right` 都 BLACK |
| 远侄红收洞 | 给 `w.right`（x 在左时）涂黑，旋父 |

### 4.5 怎么验

| 你看到的 | 判断 | 动作 |
| --- | --- | --- |
| 给一棵树问是否红黑树 | 先确认 BST，再查五条 | 根黑、无红红、每条路黑高相同 |
| 手写插入 | 新节点涂红，只修红红相邻 | 看叔叔：红就变色往上；黑就按 LL/LR/RR/RL 旋 |
| 手写删除 | 先 BST 删除；被摘的是红则停 | 被摘的是黑 → 双黑，看兄弟/侄子 |
| `TreeMap` / `HashMap` 树化 | 底层就是红黑树 | 不用自己实现 |

| | AVL | 红黑树 |
| --- | --- | --- |
| 平衡 | 左右高度差 ≤ 1，更矮 | 最长 < 2 × 最短，略高 |
| 插入/删除 | 旋转多 | 经常只变色，插入最多 2 次旋，删除最多 3 次 |
| Java 里 | 基本不用 | `TreeMap` / `TreeSet` / `HashMap` 树化 |

---

## 五、B+ Tree = 多路 + 叶子链表

二叉树再平衡，一次磁盘 IO 也只拿到 2 个孩子指针。InnoDB 一页 **16KB**，一页能放上千个目录项，树高 3 层就能装约 2000 万行。这就是 B+ 相对 AVL/红黑树换赛道的原因：**为磁盘设计，不为内存设计。**

### 5.1 和 BST 的三个差别

```text
内存里的 BST/AVL/红黑树              磁盘上的 B+ Tree

        20黑                          [10 | 20 | 30]     ← 内部节点：只放 key，当目录
       /    \                        /    |    \    \
     10红    30红                 [1..9] [10..19] [20..29] [30..]  ← 叶子：数据 + 横向链表
                                         ←  →
```

| | BST / AVL / 红黑树 | B+ Tree |
| --- | --- | --- |
| 每个节点孩子数 | 最多 2 | 最多 `MAX_KEYS + 1`（教学用 4，InnoDB 约 1200+） |
| 数据放哪 | 每个节点都可能有 | **只有叶子**有数据；内部节点是纯目录 |
| 范围查询 | 中序遍历，左右跳 | 找到起点叶子，沿 `next` 链表扫 |
| 平衡手段 | 旋转 / 变色 | **分裂 / 合并**（和页的分裂合并对应） |

B 树（B-Tree）内部节点也存数据；B+ 把数据全部沉到叶子，内部更瘦，同样一页能索引更多行。范围扫描也不用在内部节点来回跳。

InnoDB 里的对应关系（详见 MySQL 笔记，这里只记写代码时用得上的）：

| 代码里 | InnoDB |
| --- | --- |
| 一个 Node | 一个 16KB 数据页 |
| 内部节点的 key | 页目录项：主键 + 子页号 |
| 叶子节点 | 聚簇索引存整行，二级索引存主键 |
| 叶子 `next` | 页的 File Header 双向链表 |
| 树高 3 | 约 2000 万行（行约 1KB 时） |

### 5.2 节点怎么设计

教学阶数：`MAX_KEYS = 3`（每个节点最多 3 个 key、4 个孩子），纸上能画完。`MIN_KEYS = 1`，少了就借或合并。

```java
static final int MAX_KEYS = 3;
static final int MIN_KEYS = 1;

private static class Node {
    boolean leaf;
    int n;                              // 当前 key 个数
    int[] keys = new int[MAX_KEYS + 1]; // 多 1 格，先插入再分裂
    int[] vals = new int[MAX_KEYS + 1]; // 仅叶子用
    Node[] children = new Node[MAX_KEYS + 2];
    Node next;                          // 仅叶子用，串成链表
    Node parent;
}
```

内部节点的分隔 key = **右子树的最小 key**（拷贝上来的，叶子里还留着）：

```text
内部:     keys[0]=10     keys[1]=20
      children[0]    children[1]    children[2]
         < 10         10..19          ≥ 20
```

查找时：`key >= keys[i]` 就继续往右，否则进 `children[i]`。

### 5.3 查找、插入、分裂

查找：从根往下走到叶子，再在叶子里扫。和 BST「小左大右」是同一件事，只是一次比较能跳过一段。

插入：先当查找，在叶子里按序插入。满了（`n > MAX_KEYS`）就分裂。

**叶子分裂**（B+ 特有：提升的 key **拷贝**上去，叶子里还留着）：

```text
叶子溢出 [1, 2, 3, 4]        从中间切开

左 [1, 2]  →  右 [3, 4]
                ↑
          把 3 拷贝到父节点当分隔 key
```

**内部节点分裂**（中间 key **搬走**，两边都不留）：

```text
内部溢出 keys = [10, 20, 30, 40]，孩子 5 个

左保留 [10]          右保留 [40]
          20 升到父亲（30 归右）
```

根分裂：新建一个根，两个孩子，树高 +1。这就是 B+ 长高的唯一方式。

范围查询：`findLeaf(from)`，然后 `leaf = leaf.next` 一直扫到 `> to`。这是 B+ 相对 BST 中序的优势，代码也就十几行。

### 5.4 删除：借一下，不够再合并

叶子删完如果 `n < MIN_KEYS`：

1. 左兄弟 key 有多 → 借最后一个，更新父节点分隔 key
2. 否则右兄弟有多 → 借第一个
3. 都不够 → 和兄弟合并，父节点删掉对应分隔 key；父节点不够再往上修
4. 根只剩 0 个 key、1 个孩子 → 根下沉，树高 −1

和红黑树「双黑往上推」是同一类问题：洞在哪一层，哪一层补。

### 5.5 完整代码怎么读

文件：[`code/BPlusTree.java`](code/BPlusTree.java)。按这个顺序看，不要从头扫到尾：

| 顺序 | 方法 | 对应上面哪一节 |
| --- | --- | --- |
| 1 | `findLeaf` / `get` | 查找 |
| 2 | `put` → `insertIntoLeaf` → `splitLeaf` | 插入 + 叶子分裂 |
| 3 | `insertIntoParent` → `splitInternal` | 分隔 key 往上冒 |
| 4 | `range` / `allKeys` | 叶子链表 |
| 5 | `remove` → `rebalanceLeaf` → `mergeLeaves` | 删除 |
| 6 | `rebalanceInternal` / `mergeInternal` | 洞往上推 |

`main` 里已经覆盖：插 1..20、点查、`range(5,12)`、删一串后再插回来。跑通即可。

### 5.6 怎么验

| 你看到的 | 判断 | 动作 |
| --- | --- | --- |
| 插 1,2,3,4 后内部节点没有分隔 key | 根没分裂 | 叶子 `n > MAX_KEYS` 必须 `splitLeaf` |
| 范围查询漏数 / 乱序 | 叶子 `next` 没接上，或分裂时没按序切 | 画 4 个 key 的分裂，检查 `leaf.next` |
| 分隔 key 在叶子里找不到 | 当成 B 树把 key 搬走了 | B+ 叶子分裂是 **拷贝** 右叶子第一个 key |
| 删到只剩几个 key，查找失败 | 合并后父节点孩子指针没删干净 | 跟着 `removeChild` 走一遍 |
| 面试问「为什么 InnoDB 用 B+ 不用红黑树」 | 红黑树一次 IO 拿 2 路，B+ 一次拿上千路 | 再补一句：叶子链表天然适合 `BETWEEN` / 范围扫 |

---

## 六、动手清单

建议按这个顺序敲，每一步都能单独跑：

1. **BST**：增、查、删（含两个孩子），中序有序。故意插 `10,20,30`，接受根是 10
2. **AVL**：Node 加 `height`，旋转 + `rebalance`，同样插 `10,20,30`，根必须变成 20。再测 LR / RL
3. **红黑树**：加 NIL 和 parent，先把插入三问写对（每插一个就 `validate`），再写删除四问
4. **B+ Tree**：先写查找 + 插入分裂 + `range`，最后写删除。`MAX_KEYS = 3` 方便对照纸面

四份完整代码：

| 文件 | 跑通标志 |
| --- | --- |
| [`code/BinarySearchTree.java`](code/BinarySearchTree.java) | `BST_OK` |
| [`code/AvlTree.java`](code/AvlTree.java) | `AVL_OK` |
| [`code/RedBlackTree.java`](code/RedBlackTree.java) | `RBT_OK` |
| [`code/BPlusTree.java`](code/BPlusTree.java) | `BPT_OK` |

```bash
cd 算法/二叉树/code
javac -encoding UTF-8 *.java
java BinarySearchTree
java AvlTree
java RedBlackTree
java BPlusTree
```

写生产代码时：有序 Map 用 `TreeMap`（红黑树），磁盘索引的设计对齐 B+ Tree；手写这四棵是为了看见「左小右大」之后，到底是高度、颜色、还是页在维持平衡。
