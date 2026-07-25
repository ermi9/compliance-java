import ast

code = """
class Animal:
    def __init__(self, name):
        self.__name = name
    
    def speak(self):
        return self.__name

class Dog(Animal):
    def speak(self):
        return super().speak() + " barks"
"""

tree = ast.parse(code)
print(ast.dump(tree, indent=2))
