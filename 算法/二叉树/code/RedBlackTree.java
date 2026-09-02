public class RedBlackTree {

    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private static class Node {
        int val;
        boolean color;
        Node left;
        Node right;
        Node parent;

        Node(int val, boolean color) {
            this.val = val;
            this.color = color;
        }
    }

    private final Node nil;
    private Node root;

    public RedBlackTree() {
        nil = new Node(0, BLACK);
        nil.left = nil;
        nil.right = nil;
        nil.parent = nil;
        root = nil;
    }

    public boolean contains(int value) {
        return find(value) != nil;
    }

    private Node find(int value) {
        Node cur = root;
        while (cur != nil) {
            if (value == cur.val) {
                return cur;
            }
            cur = value < cur.val ? cur.left : cur.right;
        }
        return nil;
    }

    private Node minimum(Node node) {
        while (node.left != nil) {
            node = node.left;
        }
        return node;
    }

    private void rotateLeft(Node x) {
        Node y = x.right;
        x.right = y.left;
        if (y.left != nil) {
            y.left.parent = x;
        }
        transplantParent(x, y);
        y.left = x;
        x.parent = y;
    }

    private void rotateRight(Node x) {
        Node y = x.left;
        x.left = y.right;
        if (y.right != nil) {
            y.right.parent = x;
        }
        transplantParent(x, y);
        y.right = x;
        x.parent = y;
    }

    private void transplantParent(Node x, Node y) {
        y.parent = x.parent;
        if (x.parent == nil) {
            root = y;
        } else if (x == x.parent.left) {
            x.parent.left = y;
        } else {
            x.parent.right = y;
        }
    }

    public void insert(int value) {
        Node y = nil;
        Node x = root;
        while (x != nil) {
            y = x;
            if (value == x.val) {
                return;
            }
            x = value < x.val ? x.left : x.right;
        }
        Node z = new Node(value, RED);
        z.left = nil;
        z.right = nil;
        z.parent = y;
        if (y == nil) {
            root = z;
        } else if (value < y.val) {
            y.left = z;
        } else {
            y.right = z;
        }
        insertFixup(z);
    }

    private void insertFixup(Node z) {
        while (z.parent.color == RED) {
            if (z.parent == z.parent.parent.left) {
                Node uncle = z.parent.parent.right;
                if (uncle.color == RED) {
                    z.parent.color = BLACK;
                    uncle.color = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.right) {
                        z = z.parent;
                        rotateLeft(z);
                    }
                    z.parent.color = BLACK;
                    z.parent.parent.color = RED;
                    rotateRight(z.parent.parent);
                }
            } else {
                Node uncle = z.parent.parent.left;
                if (uncle.color == RED) {
                    z.parent.color = BLACK;
                    uncle.color = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.left) {
                        z = z.parent;
                        rotateRight(z);
                    }
                    z.parent.color = BLACK;
                    z.parent.parent.color = RED;
                    rotateLeft(z.parent.parent);
                }
            }
        }
        root.color = BLACK;
    }

    public void delete(int value) {
        Node z = find(value);
        if (z == nil) {
            return;
        }
        Node y = z;
        boolean yOriginalColor = y.color;
        Node x;
        if (z.left == nil) {
            x = z.right;
            transplant(z, z.right);
        } else if (z.right == nil) {
            x = z.left;
            transplant(z, z.left);
        } else {
            y = minimum(z.right);
            yOriginalColor = y.color;
            x = y.right;
            if (y.parent == z) {
                x.parent = y;
            } else {
                transplant(y, y.right);
                y.right = z.right;
                y.right.parent = y;
            }
            transplant(z, y);
            y.left = z.left;
            y.left.parent = y;
            y.color = z.color;
        }
        if (yOriginalColor == BLACK) {
            deleteFixup(x);
        }
    }

    private void transplant(Node u, Node v) {
        if (u.parent == nil) {
            root = v;
        } else if (u == u.parent.left) {
            u.parent.left = v;
        } else {
            u.parent.right = v;
        }
        v.parent = u.parent;
    }

    private void deleteFixup(Node x) {
        while (x != root && x.color == BLACK) {
            if (x == x.parent.left) {
                Node w = x.parent.right;
                if (w.color == RED) {
                    w.color = BLACK;
                    x.parent.color = RED;
                    rotateLeft(x.parent);
                    w = x.parent.right;
                }
                if (w.left.color == BLACK && w.right.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.right.color == BLACK) {
                        w.left.color = BLACK;
                        w.color = RED;
                        rotateRight(w);
                        w = x.parent.right;
                    }
                    w.color = x.parent.color;
                    x.parent.color = BLACK;
                    w.right.color = BLACK;
                    rotateLeft(x.parent);
                    x = root;
                }
            } else {
                Node w = x.parent.left;
                if (w.color == RED) {
                    w.color = BLACK;
                    x.parent.color = RED;
                    rotateRight(x.parent);
                    w = x.parent.left;
                }
                if (w.right.color == BLACK && w.left.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.left.color == BLACK) {
                        w.right.color = BLACK;
                        w.color = RED;
                        rotateLeft(w);
                        w = x.parent.left;
                    }
                    w.color = x.parent.color;
                    x.parent.color = BLACK;
                    w.left.color = BLACK;
                    rotateRight(x.parent);
                    x = root;
                }
            }
        }
        x.color = BLACK;
    }

    public Integer rootVal() {
        return root == nil ? null : root.val;
    }

    public String inorder() {
        StringBuilder sb = new StringBuilder();
        inorderRec(root, sb);
        return sb.toString().trim();
    }

    private void inorderRec(Node node, StringBuilder sb) {
        if (node == nil) {
            return;
        }
        inorderRec(node.left, sb);
        sb.append(node.val).append(' ');
        inorderRec(node.right, sb);
    }

    void validate() {
        if (root != nil && root.color != BLACK) {
            throw new AssertionError("root must be black");
        }
        blackHeight(root);
    }

    private int blackHeight(Node node) {
        if (node == nil) {
            return 1;
        }
        if (node.color == RED) {
            if (node.left.color == RED || node.right.color == RED) {
                throw new AssertionError("red-red at " + node.val);
            }
        }
        if (node.left != nil && node.left.parent != node) {
            throw new AssertionError("left parent broken at " + node.val);
        }
        if (node.right != nil && node.right.parent != node) {
            throw new AssertionError("right parent broken at " + node.val);
        }
        if (node.left != nil && node.left.val >= node.val) {
            throw new AssertionError("BST left broken at " + node.val);
        }
        if (node.right != nil && node.right.val <= node.val) {
            throw new AssertionError("BST right broken at " + node.val);
        }
        int lh = blackHeight(node.left);
        int rh = blackHeight(node.right);
        if (lh != rh) {
            throw new AssertionError("black-height mismatch at " + node.val);
        }
        return lh + (node.color == BLACK ? 1 : 0);
    }

    public static void main(String[] args) {
        RedBlackTree t = new RedBlackTree();
        int[] insert = {10, 5, 15, 3, 7, 12, 18, 1, 4, 6, 8, 11, 13, 16, 19, 2, 9, 14, 17, 20};
        for (int v : insert) {
            t.insert(v);
            t.validate();
        }
        if (!"1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20".equals(t.inorder())) {
            throw new AssertionError("inorder: " + t.inorder());
        }
        int[] del = {1, 10, 20, 7, 15, 3, 18, 2, 19, 4, 16, 5, 17, 6, 14, 8, 13, 9, 12, 11};
        for (int v : del) {
            t.delete(v);
            t.validate();
            if (t.contains(v)) {
                throw new AssertionError("still contains " + v);
            }
        }
        if (!t.inorder().isEmpty()) {
            throw new AssertionError("should be empty: " + t.inorder());
        }
        System.out.println("RBT_OK");
    }
}
