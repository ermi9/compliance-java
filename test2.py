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

