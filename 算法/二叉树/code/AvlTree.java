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

    public Integer rootVal() {
        return root == null ? null : root.val;
    }

    public String inorder() {
        StringBuilder sb = new StringBuilder();
        inorderRec(root, sb);
        return sb.toString().trim();
    }

    private void inorderRec(Node node, StringBuilder sb) {
        if (node == null) {
            return;
        }
        inorderRec(node.left, sb);
        sb.append(node.val).append(' ');
        inorderRec(node.right, sb);
    }

    private void assertBalanced(Node node) {
        if (node == null) {
            return;
        }
        assertBalanced(node.left);
        assertBalanced(node.right);
        int expected = 1 + Math.max(h(node.left), h(node.right));
        if (node.height != expected) {
            throw new AssertionError("height stale at " + node.val);
        }
        if (Math.abs(bf(node)) > 1) {
            throw new AssertionError("unbalanced at " + node.val + " bf=" + bf(node));
        }
    }

    public static void main(String[] args) {
        AvlTree avl = new AvlTree();
        avl.insert(10);
        avl.insert(20);
        avl.insert(30);
        if (avl.rootVal() == null || avl.rootVal() != 20) {
            throw new AssertionError("RR 后根必须是 20，实际 " + avl.rootVal());
        }
        if (!"10 20 30".equals(avl.inorder())) {
            throw new AssertionError(avl.inorder());
        }
        avl.assertBalanced(avl.root);

        AvlTree lr = new AvlTree();
        lr.insert(30);
        lr.insert(10);
        lr.insert(20);
        if (lr.rootVal() == null || lr.rootVal() != 20) {
            throw new AssertionError("LR 后根必须是 20");
        }
        lr.assertBalanced(lr.root);

        AvlTree ll = new AvlTree();
        ll.insert(30);
        ll.insert(20);
        ll.insert(10);
        if (ll.rootVal() == null || ll.rootVal() != 20) {
            throw new AssertionError("LL 后根必须是 20");
        }

        AvlTree rl = new AvlTree();
        rl.insert(10);
        rl.insert(30);
        rl.insert(20);
        if (rl.rootVal() == null || rl.rootVal() != 20) {
            throw new AssertionError("RL 后根必须是 20");
        }

        for (int i = 1; i <= 20; i++) {
            avl.insert(i);
            avl.assertBalanced(avl.root);
        }
        avl.delete(20);
        avl.delete(10);
        avl.delete(15);
        avl.assertBalanced(avl.root);
        System.out.println("AVL_OK");
    }
}
