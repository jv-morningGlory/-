# AVL 树：遇到不平衡怎么修

> 约定：**高度 h** = 空树 0、叶子 1；**平衡因子**（**Balance Factor**，bf）= h(左) − h(右)。部分教材用「右−左」，做题前先对口径，判型逻辑一样。

---

## 一、什么叫不平衡

**AVL** 要求任意节点 **|bf| ≤ 1**。一旦出现 **|bf| = 2**，这棵子树就必须旋转。

| bf | 状态 | 动作 |
| --- | --- | --- |
| +1 / 0 / −1 | 平衡 | 不用动 |
| **+2** | 左子树比右子树高 2 | 要往回扳（最终右旋） |
| **−2** | 右子树比左子树高 2 | 要往回扳（最终左旋） |

h 必须**自底向上**标：先叶子，再往根走。任何节点的 h 都依赖孩子，从上往下标必错。

---

## 二、现场作业：固定 4 步

插入或删除后，不要盯整棵树，按这条流水线走：

1. **只看一条路**：从被改的节点走到根，只重算这条路径上的 h / bf
2. **找最低失衡点**：路上第一个 `|bf| = 2` 的节点，记为 **A**（先修它，别跳）
3. **看两个数定类型**：`bf(A)` + `bf(A 的重侧孩子)` → 对照第三节的表
4. **旋转一次（或两次）**，旋转后继续往上检查，直到根，或高度不再变

> 口诀：**从下往上找，谁先爆 2 修谁；看 A 定方向，看孩子定单双。**

---

## 三、四种失衡：对照表 + 最小例子

命名：**第一个字母 = A 哪侧重，第二个字母 = 重侧孩子哪侧重**。同向单旋，拐弯双旋。

| bf(A) | 重侧孩子的 bf | 类型 | 操作 |
| --- | --- | --- | --- |
| +2 左重 | +1（删除时也可能是 0） | **LL** | 对 A **右旋** 1 次 |
| +2 左重 | −1 | **LR** | 先对左孩子**左旋**，再对 A **右旋** |
| −2 右重 | −1（删除时也可能是 0） | **RR** | 对 A **左旋** 1 次 |
| −2 右重 | +1 | **RL** | 先对右孩子**右旋**，再对 A **左旋** |

> 孩子 bf=0 **只出现在删除**：仍按单旋做。插新节点时，孩子必是 ±1。

### 3.1 LL：连续往左偏 → 右旋

插入 `30, 20, 10`：

```text
    30(+2)              20
   /                   /  \
  20(+1)      →      10    30
 /
10
```

A=30 左重，左孩子 20 也左重 → LL。把 20 提上去，30 倒向右边。

### 3.2 RR：连续往右偏 → 左旋

插入 `10, 20, 30`：

```text
10(-2)                  20
  \                    /  \
  20(-1)      →      10    30
    \
    30
```

A=10 右重，右孩子 20 也右重 → RR。把 20 提上去，10 倒向左边。**和 LL 镜像。**

### 3.3 LR：先往左再往右 → 先左旋再右旋

插入 `30, 10, 20`：

```text
    30(+2)          30(+2)              20
   /               /                   /  \
  10(-1)   →     20(+1)       →      10    30
    \            /
    20          10
```

A=30 左重，但左孩子 10 是**右重**（拐弯了）。先把 20 转到 10 的位置（变成 LL），再右旋 30。

### 3.4 RL：先往右再往左 → 先右旋再左旋

插入 `10, 30, 20`：

```text
10(-2)              10(-2)              20
  \                   \                /  \
  30(+1)     →        20(-1)   →     10    30
  /                     \
 20                     30
```

A=10 右重，右孩子 30 是**左重**。先把 20 转到 30 的位置（变成 RR），再左旋 10。

---

## 四、旋转时手怎么动

旋转只动 2~3 个指针，**中序顺序不变**（BST 性质保住）。怕写反就记：被顶上来的节点，它「内侧」那棵子树要过继给降下去的节点。

### 右旋（LL 的那一下）

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

### 左旋（RR 的那一下）

```text
    A                         B
   / \                       / \
  T1  B         →          A     C
     / \                  / \
    T2  C               T1  T2
```

1. B = A.right，B 升为新根
2. **T2（B 的左子树）过继给 A 当右孩子**
3. A 降为 B 的左孩子

双旋没有第三种旋转：LR = 对左孩子做一次左旋 + 对 A 做一次右旋；RL 反之。

---

## 五、修完还要不要往上走

| 场景 | 失衡点个数 | 原因 |
| --- | --- | --- |
| 插入 | **最多 1 个** | 旋转后这棵子树高度回到插入前，上面的 bf 不会再爆 |
| 删除 | **可能多个** | 旋转后子树可能比删除前**再矮 1**，矮会继续往上传 |

删除后，某一侧刚变矮，看祖先 X 原来的 bf：

| X 原来的 bf | 变矮的一侧 | 结果 | 还要不要往上 |
| --- | --- | --- | --- |
| 0 | 任意 | 变成 ±1，**高度不变** | **停** |
| ±1 | 本来就矮的那侧 | 变成 ±2 → 再转一次 | 转完再看高度 |
| ±1 | 本来高的那侧 | 变成 0，高度 −1 | **继续** |

> 删除时还有一种：失衡点的重侧孩子 bf=0，单旋后高度**不降**，调整到此结束。

---

## 六、完整走一遍：删除导致两次失衡

下面这棵是合法 AVL。题目：**删 15**。

```text
              20
            /    \
          10      40
         /  \    /  \
        5   15  30  50
       /       / \    \
      1      25  35   55
              \
              28
```

先在纸上标一遍关键节点的 h / bf，后面才知道谁爆了：

| 节点 | h(左) | h(右) | bf |
| --- | --- | --- | --- |
| 10 | 2 | 1 | +1 |
| 40 | 3 | 2 | +1 |
| 20 | 3 | 4 | −1 |

### 第 1 个失衡点：10（LL）

删叶子 15，从 10 往上重算：

- 10：左 2、右 0 → **bf=+2**，这就是最低失衡点
- 左孩子 5 的 bf=+1 → **同向 → LL**
- 对 10 **右旋**

```text
      10                  5
     /                   / \
    5          →       1   10
   /
  1
```

这棵子树高度 3 → 2，比删除前矮了，**必须继续往上**。

### 第 2 个失衡点：20（RL）

- 20 左边从 3 降到 2，右边仍是 4 → **bf=−2**
- 右孩子 40 的 bf=+1 → **反向 → RL**
- 先对 40 **右旋**（30 上来），再对 20 **左旋**（30 成为整棵树新根）

```text
              30
            /    \
          20      40
         /  \    /  \
        5   25  35  50
       / \    \       \
      1  10   28      55
```

到根了，结束。收尾只验三件事：

1. 每个节点 |bf| ≤ 1
2. 中序仍有序：`1 5 10 20 25 28 30 35 40 50 55`
3. 高度 5 → 4，仍然是 AVL

---

## 七、代码：rebalance 就是那张表

节点里存 `height`，避免每次重算整棵子树。插入/删除回溯时对每个节点调用一次 `rebalance`，顺序自动是「先修最低的」。

```java
class AvlNode {
    int val, height = 1;
    AvlNode left, right;
    AvlNode(int val) { this.val = val; }
}

int h(AvlNode n) { return n == null ? 0 : n.height; }

void refresh(AvlNode n) {
    n.height = 1 + Math.max(h(n.left), h(n.right));
}

int bf(AvlNode n) {
    return n == null ? 0 : h(n.left) - h(n.right);
}

AvlNode rotateRight(AvlNode a) {
    AvlNode b = a.left;
    a.left = b.right;   // T3 过继
    b.right = a;
    refresh(a);
    refresh(b);
    return b;
}

AvlNode rotateLeft(AvlNode a) {
    AvlNode b = a.right;
    a.right = b.left;   // T2 过继
    b.left = a;
    refresh(a);
    refresh(b);
    return b;
}

AvlNode rebalance(AvlNode a) {
    refresh(a);
    if (bf(a) > 1) {                 // 左重
        if (bf(a.left) < 0) {        // LR：先拧直
            a.left = rotateLeft(a.left);
        }
        return rotateRight(a);       // LL，或 LR 的第二步
    }
    if (bf(a) < -1) {                // 右重
        if (bf(a.right) > 0) {       // RL：先拧直
            a.right = rotateRight(a.right);
        }
        return rotateLeft(a);        // RR，或 RL 的第二步
    }
    return a;
}
```

> 生产环境用 `TreeMap` / `TreeSet`（红黑树）。手写 AVL 是为了考试时能在纸上把四种旋转做对。

---

## 八、从 BST 改成 AVL（动手）

AVL = BST + 每个节点记高度 + 插入/删除回溯时 `rebalance`。

查找、中序打印、`findMin` **一行都不用改**。只动 Node、insert、delete。

### 第 1 步：Node 加 `height`

叶子高度约定为 **1**，空树为 **0**（和本文开头一致）。

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

去掉 `@Data`，内部类直接访问字段。

### 第 2 步：三个工具函数

每次改完孩子，必须先刷新自己的 height，再算 bf。

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

### 第 3 步：左旋 / 右旋

旋转只动指针，中序顺序不变。先 `refresh` 降下去的节点，再 `refresh` 升上来的（高度依赖孩子）。

```java
private Node rotateRight(Node a) {
    Node b = a.left;
    a.left = b.right;  // T3 过继给 A
    b.right = a;
    refresh(a);
    refresh(b);
    return b;          // B 变成这棵子树的新根
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

### 第 4 步：`rebalance` = 第三节那张表

```java
private Node rebalance(Node a) {
    refresh(a);
    if (bf(a) > 1) {              // 左重 → 最终右旋
        if (bf(a.left) < 0) {     // LR：先把左孩子拧直
            a.left = rotateLeft(a.left);
        }
        return rotateRight(a);
    }
    if (bf(a) < -1) {             // 右重 → 最终左旋
        if (bf(a.right) > 0) {    // RL：先把右孩子拧直
            a.right = rotateRight(a.right);
        }
        return rotateLeft(a);
    }
    return a;                     // |bf| ≤ 1，不用转
}
```

### 第 5 步：insert / delete 回溯时挂上 rebalance

这是相对 BST **唯一的行为变化**：原来 `return node`，现在 `return rebalance(node)`。

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
            return node.right;   // 节点已删，父节点回溯时再 rebalance
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

> 0/1 个孩子时直接 `return` 孩子，不要对已删除的节点 rebalance。两个孩子时：先用后继替换，再对当前节点 rebalance。

### 怎么验：必须用会旋转的序列

`5,3,7,2,4,6,8` 本来就是平衡的，看不出 AVL 和 BST 的差别。用连续递增：

```java
AvlTree avl = new AvlTree();
avl.insert(10);
avl.insert(20);
avl.insert(30);  // BST 会歪成一条链；AVL 必须变成 20 为根
avl.print();     // 10 20 30
```

| 步骤 | BST（不旋转） | AVL |
| --- | --- | --- |
| 插 10 | `10` | `10` |
| 插 20 | `10 → 20` | `10 → 20` |
| 插 30 | `10 → 20 → 30`（退化） | 对 10 **左旋** → `20` 为根，左右 10、30 |

中序仍是 `10 20 30`（BST 性质没丢），但树高从 3 变成 2。

---

## 九、完整代码

```java
public class AvlTree {

    private Node root;

    private static class Node {
        int val;
        int height = 1;
        Node left;
        Node right;

        Node(int val) {
            this.val = val;
        }
    }

    private int h(Node n) {
        return n == null ? 0 : n.height;
    }

    private void refresh(Node n) {
        n.height = 1 + Math.max(h(n.left), h(n.right));
    }

    private int bf(Node n) {
        return n == null ? 0 : h(n.left) - h(n.right);
    }

    private Node rotateRight(Node a) {
        Node b = a.left;
        a.left = b.right;
        b.right = a;
        refresh(a);
        refresh(b);
        return b;
    }

    private Node rotateLeft(Node a) {
        Node b = a.right;
        a.right = b.left;
        b.left = a;
        refresh(a);
        refresh(b);
        return b;
    }

    private Node rebalance(Node a) {
        refresh(a);
        if (bf(a) > 1) {
            if (bf(a.left) < 0) {
                a.left = rotateLeft(a.left);
            }
            return rotateRight(a);
        }
        if (bf(a) < -1) {
            if (bf(a.right) > 0) {
                a.right = rotateRight(a.right);
            }
            return rotateLeft(a);
        }
        return a;
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
        return rebalance(node);
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
        } else if (value > node.val) {
            node.right = deleteRec(node.right, value);
        } else {
            if (node.left == null) {
                return node.right;
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
        AvlTree avl = new AvlTree();
        avl.insert(10);
        avl.insert(20);
        avl.insert(30);
        avl.print();           // 10 20 30，根必须是 20

        avl.delete(20);
        avl.print();           // 10 30
    }
}
```

判断对不对，只看三件事：

1. 中序仍然有序
2. 连续插 `10,20,30` 后根是 20，不是 10
3. 每个节点 `|bf| ≤ 1`（自己在 `rebalance` 后 assert，或 debug 打印 `bf`）
