package example;

public class ExampleChild extends ExampleParent {

    public ExampleChild() { super(); }

    public int magicNumber() {
        return super.magicNumber() * 2;
    }
}
