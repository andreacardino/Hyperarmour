 Every player character attack and every enemy attack is represented as a node in a directed acyclic graph (DAG).
 
 The edge from node n to node n' is a boolean, a truth value, indicating whether it is true that n < n' or not.\n
 Let C be the set of nodes representing character attacks, E the set of nodes representing enemy attacks.\n
 Then, the stopping condition for a Monte Carlo simulation can be formalised as: ∀c ∈ C, ∀e ∈ E: c < e ∨ e < c.\n
 In other words, the learning process is over once we know every character attack to either withstand or be interrupted by every enemy attack\n 
 (should H(c) and D(e) be equal, it would be the case that c < e is true, since the interaction would result in a stagger).

M being the number of character attacks, N the number of enemy attacks, the amount of nodes in the DAG is M + N. With the previously assumed data for this system, M + N = 300 + 600 = 900.\n
The number of edges between nodes is the square of the amount of nodes, 810,000 with our data.\n
Computationally, this graph of nodes is implemented as a matrix.\n
To provide a visual, digestible example, let's consider a small subset of this matrix: 3 character attacks and 6 enemy attacks.\n
C = {c1, c2, c3}\n
E = {e1, e2, e3, e4, e5, e6}\n
In the beginning, before any observation, every cell in the matrix, every edge, is set to false:\n
![initial matrix](https://github.com/andreacardino/Hyperarmour/blob/main/initialMatrix.png)
