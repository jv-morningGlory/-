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

    public static void main(String[] args) {
        BinarySearchTree bst = new BinarySearchTree();
        int[] vals = {5, 3, 7, 2, 4, 6, 8};
        for (int v : vals) {
            bst.insert(v);
        }
        if (!"2 3 4 5 6 7 8".equals(bst.inorder())) {
            throw new AssertionError("inorder after insert: " + bst.inorder());
        }
        bst.delete(5);
        if (!"2 3 4 6 7 8".equals(bst.inorder())) {
            throw new AssertionError("inorder after delete 5: " + bst.inorder());
        }
        if (bst.contains(5) || !bst.contains(6)) {
            throw new AssertionError("contains after delete");
        }
        BinarySearchTree chain = new BinarySearchTree();
        chain.insert(10);
        chain.insert(20);
        chain.insert(30);
        if (chain.rootVal() == null || chain.rootVal() != 10) {
            throw new AssertionError("BST 有序插入会退化，根应仍是 10");
        }
        System.out.println("BST_OK");
    }
}
