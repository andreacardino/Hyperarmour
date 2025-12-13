 Every player character attack and every enemy attack is represented as a node in a directed acyclic graph (DAG).  
 The edge from a node *n* to another node *n'* is a boolean, a truth value, indicating whether *n < n'* or not.  
 Let *less(n, n')* be the function denoting this relationship.
 Let *C* be the set of nodes representing character attacks, *E* the set of nodes representing enemy attacks.  
 Then, the stopping condition for a Monte Carlo simulation can be formalised as:  
 ![stopping condition](https://raw.githubusercontent.com/andreacardino/Hyperarmour/refs/heads/main/stopping%20condition.svg)
 In other words, the learning process is over once we know every character attack to either withstand or be interrupted by every enemy attack  
 (should *H(c)* and *D(e)* be equal, it would be the case that *less(c, e) = true*, since the interaction would result in a stagger).  

*M* being the number of character attacks, *N* the number of enemy attacks, the amount of nodes in the DAG is *M + N*. With the previously assumed data for this system, *M + N* = 300 + 600 = 900.  
The number of edges between nodes is the square of the amount of nodes, 810,000 with our data.  
Computationally, this graph of nodes is implemented as a matrix.  
To provide a visual, digestible example, let's consider a small subset of this matrix: 3 character attacks and 6 enemy attacks.  
*C = {c1, c2, c3}*  
*E = {e1, e2, e3, e4, e5, e6}*  
In the beginning, before any observation, every cell in the matrix, every edge, is set to false:  

![initial matrix](https://github.com/andreacardino/Hyperarmour/blob/main/initialMatrix.png)

Then, upon observing an interaction between an element of *C* and an element of *E*, e.g. *c2 < e4*, knowledge is updated by applying transitive closure to the relation space.  
This transitive closure can be formalised as: *∀i(less(i, c2) V i = c2 -> ∀j(less(e4, j) V j = c2 -> less(i, j) = true))*.  
**The formula captures the 3 inferences triggered by discovering that *less(c2, e4)*:**  
**1) everything weaker than *c2* must be weaker than *e4***  
**2) everything stronger than *e4* must be stronger than *c2***  
**3) everything weaker than *c2* must be weaker than everything stronger than *e2***  
