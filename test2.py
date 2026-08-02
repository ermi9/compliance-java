import ast

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

tree1 = ast.parse(compliant)
tree2 = ast.parse(non_compliant)

def count_classes(tree):
    return sum(1 for node in ast.walk(tree) 
               if isinstance(node, ast.ClassDef))

def count_functions(tree):
    return sum(1 for node in ast.walk(tree) 
               if isinstance(node, ast.FunctionDef))

def get_inheritance(tree):
    return [(c.name, [b.id for b in c.bases if isinstance(b, ast.Name)]) 
            for c in ast.walk(tree) 
            if isinstance(c, ast.ClassDef)]

print("=== COMPLIANT ===")
print(f"Classes: {count_classes(tree1)}")
print(f"Functions: {count_functions(tree1)}")
print(f"Inheritance: {get_inheritance(tree1)}")

print("\n=== NON COMPLIANT ===")
print(f"Classes: {count_classes(tree2)}")
print(f"Functions: {count_functions(tree2)}")
print(f"Inheritance: {get_inheritance(tree2)}")
