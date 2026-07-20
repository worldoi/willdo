import re, sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
# strip block comments
s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
# strip line comments
s = re.sub(r'//[^\n]*', '', s)
# strip string and char literals (handles escaped quotes)
s = re.sub(r'"(?:[^"\\]|\\.)*"', '', s)
s = re.sub(r"'(?:[^'\\]|\\.)*'", '', s)
# also strip Kotlin triple-quoted strings
s = re.sub(r'""".*?"""', '', s, flags=re.S)
print('curly  open=%d close=%d net=%d' % (s.count('{'), s.count('}'), s.count('{')-s.count('}')))
print('paren  open=%d close=%d net=%d' % (s.count('('), s.count(')'), s.count('(')-s.count(')')))
