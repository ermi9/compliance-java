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
