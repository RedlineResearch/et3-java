// New call test
class NewCall {
    public static void main(String args[]) {
        FooClass foo = new FooClass();
        foo.setX(914);
        foo.setNext(new FooClass());
    }
}
