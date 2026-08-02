import ast
from zss import simple_distance, Node

def ast_to_zss(node):
    """Convert Python AST node to zss Node for distance computation."""
    label = type(node).__name__
    zss_node = Node(label)
    for child in ast.iter_child_nodes(node):
        zss_node.addkid(ast_to_zss(child))
    return zss_node

compliant = """
class Animal:
    def __init__(self, name):
        self.__name = name
    def speak(self):
        return self.__name

class Dog(Animal):
    def speak(self):
        return super().speak() + " barks"
"""

non_compliant = """
def make_animal(name):
    return name

def make_dog(name):
    return make_animal(name) + " barks"
"""

borderline = """
class Animal:
    def __init__(self, name):
        self.name = name

class Dog(Animal):
    pass
"""

reference = ast.parse(compliant)
student_good = ast.parse(compliant)
student_bad = ast.parse(non_compliant)
student_borderline = ast.parse(borderline)

ref_tree = ast_to_zss(reference)

d1 = simple_distance(ref_tree, ast_to_zss(student_good))
d2 = simple_distance(ref_tree, ast_to_zss(student_bad))
d3 = simple_distance(ref_tree, ast_to_zss(student_borderline))

print(f"Compliant student distance:    {d1}")
print(f"Non-compliant student distance: {d2}")
print(f"Borderline student distance:    {d3}")
print()
print(f"Threshold suggestion: {(d1 + d2) / 2:.1f}")
