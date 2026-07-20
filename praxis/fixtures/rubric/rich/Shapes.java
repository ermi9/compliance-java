import java.util.ArrayList;
import java.util.List;

/** A compact project that demonstrates most rubric concepts, for the demonstration-check tests. */
interface Drawable { // abstraction + subtyping target
    String render();
}

abstract class Shape implements Drawable { // abstraction (abstract class) + subtyping
    private final String name; // information hiding

    Shape(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    abstract double area();

    @Override
    public String render() {
        return name + ":" + area();
    }
}

class Circle extends Shape { // inheritance
    private final double r;

    Circle(double r) {
        super("circle");
        this.r = r;
    }

    Circle() { // overloaded constructor (ad-hoc polymorphism)
        this(1.0);
    }

    @Override
    double area() { // override (inclusion base)
        return 3.14 * r * r;
    }

    double area(double scale) { // overloaded method (ad-hoc polymorphism)
        return scale * area();
    }
}

class Square extends Shape { // inheritance + a second override target for dispatch
    private final double side;

    Square(double side) {
        super("square");
        this.side = side;
    }

    @Override
    double area() {
        return side * side;
    }
}

class Box<T> { // parametric polymorphism (generic type)
    private final T value;

    Box(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}

class ShapeException extends Exception { // exception handling (custom exception)
    ShapeException(String message) {
        super(message);
    }
}

class Canvas {
    private final List<Shape> shapes = new ArrayList<>(); // composition (has-a Shape)

    void add(Shape s) {
        shapes.add(s);
    }

    String drawAll() throws ShapeException { // throws (exception usage)
        if (shapes.isEmpty()) {
            throw new ShapeException("empty");
        }
        StringBuilder sb = new StringBuilder();
        for (Shape s : shapes) {
            sb.append(s.area()); // inclusion dispatch: area() via a Shape reference, overridden in subtypes
        }
        return sb.toString();
    }

    Drawable first() {
        Shape s = new Circle(); // coercion: Circle instance bound to a Shape (variable)
        s.area();
        return new Square(2.0); // coercion: Square instance used as a Drawable (return)
    }

    static <E> E identity(E e) { // parametric polymorphism (generic method)
        return e;
    }
}
