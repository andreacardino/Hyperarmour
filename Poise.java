package soulsestimations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class Poise {
	static class Stance {
		private int value;
		/*
		 * - how many armours grant this poise, or
		 * - how many character attacks grant this hyperarmour, or
		 * - how many enemy attacks deal this stance damage
		 */
		private int count;
		/**An object of this class can either be an instance of poise, hyperarmour or stance damage*/
		public Stance(int value, int count) {
			setValue(value);
			setCount(count);
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}

		public int getCount() {
			return count;
		}

		public void setCount(int count) {
			this.count = count;
		}
	}
    
	//150 enemy attacks dealing 30 stance damage, 200 enemy attacks dealing 60 stance damage, etc.
    static final List<Stance> DAMAGES = configure(new Stance(30, 150), new Stance(60, 200), new Stance(90, 200), 
    		new Stance(120, 50));
    //# of enemy attacks
    static final int N = DAMAGES.stream().mapToInt(Stance::getCount).sum();
   
    public static void main(String[] args) {
    	//7 armours granting 31 poise, 6 armours granting 46 poise, etc.
        List<Stance> poises = configure(new Stance(31, 7), new Stance(46, 6), new Stance(61, 12),
        	    new Stance(76, 9), new Stance(91, 5), new Stance(106, 3));
        //We'll store in this List all the possible poise permutations
        List<List<Stance>> permutations = new ArrayList<>();
        permute(poises, 0, permutations);
        //Associate each permutation of poises with its likelihood
        Map<List<Stance>, Double> permProbMap = new HashMap<>();
        permutations.forEach(p -> permProbMap.put(p, permutationProbability(p)));
        double actualComplexity = computeComplexity(poises, DAMAGES, permProbMap);
        double naiveComplexity = N * poises.size();
        System.out.println("Poise: " + actualComplexity + ", " + Math.round(100 * actualComplexity/naiveComplexity) +
        "% of naive complexity " );
       
        //Fully disclosed hyperarmour system
        //80 character attacks granting 31 hyperarmour, 100 character attacks granting 61 hyperarmour, etc.
        List<Stance> hyperarmours = configure(new Stance(31, 80), new Stance(61, 100), new Stance(91, 60), 
        		new Stance(121, 10));
        permutations = new ArrayList<>();
        permute(hyperarmours, 0, permutations);
        Map<List<Stance>, Double> haMap = new HashMap<>();
        permutations.forEach(p -> haMap.put(p, permutationProbability(p)));
        hyperarmours.sort((h1, h2) -> Integer.compare(h1.getValue(), h2.getValue()));
        actualComplexity = computeComplexity(hyperarmours, DAMAGES, haMap);
        naiveComplexity = N * hyperarmours.size();
        System.out.println("Hyperarmour(explicit): " + actualComplexity + ", " +
        Math.round(100* actualComplexity/naiveComplexity) + "% of naive complexity");
    }
   
    /**Initialises a list with the poises provided*/
    static List<Stance> configure(Stance...poises) {
	    List<Stance> list = new ArrayList<>();
	    for (Stance p : poises)
	    	list.add(p);
	    return list;
    }
   
    /**Generates all permutations of the list of poises and stores them in a list of lists*/
    static void permute(List<Stance> poises, int currentIndex, List<List<Stance>> permutations) {
        if (currentIndex == poises.size() - 1) {
            permutations.add(new ArrayList<Stance>(poises));
        }
        for (int i = currentIndex; i < poises.size(); i++) {
            Collections.swap(poises, currentIndex, i);
            permute(poises, currentIndex + 1, permutations);
            Collections.swap(poises, i, currentIndex); 
        }
    }
   
    /**Computes the probability of obtaining the specified permutation of poises under weighted sampling without replacement,
     * where the weight of each poise is the # of armours granting it*/
    static double permutationProbability(List<Stance> permutation) {
        int total = permutation.stream().mapToInt(Stance::getCount).sum();
        List<Double> multiplicands = new ArrayList<>();
        for (int i = permutation.size() - 1; i >= 0; i--) {
            double m = ((double) permutation.get(i).getCount())/(total -
            permutation.subList(0, i).stream().mapToInt(Stance::getCount).sum());
            multiplicands.add(m);
        }
        return multiplicands.stream().reduce(1.0, (a, b) -> a * b);
    }
    
    /**Sums the products of the expected number of poises to be tested against a D(e) falling in a given range
     * and the probability D(e) falls in that range, for each possible range where D(e) might fall.
     * The sum obtained is the expected number of poises to be tested against a random D(e).
     * This number is multiplied by the total of enemy attacks to return the whole epistemic complexity*/
    static double computeComplexity(List<Stance> poises, List<Stance> damages, Map<List<Stance>, Double> permProbMap) {
    	//Sort poises in ascending order
    	poises.sort((p1, p2) -> Integer.compare(p1.getValue(), p2.getValue()));
    	int n = poises.size();
    	double total = 0.0;
    	int tier1 = poises.get(0).getValue();
    	double lowerThanP1Prob = damages.stream().
    			filter(d -> d.getValue() < tier1).
    			mapToInt(Stance::getCount).sum() / (double) N;
    	double tests = expectedNumberOfTests(permProbMap, tier1, (a, b) -> a < b);
    	total += tests * lowerThanP1Prob;
    	for (int i = 0; i < n - 1; i++) {
    		int lower = poises.get(i).getValue();
    		int upper = poises.get(i+1).getValue();
    		double prob = damages.stream().
    				filter(d -> d.getValue() > lower && d.getValue() < upper).
    				mapToInt(Stance::getCount).sum() / (double) N;
    		tests = expectedNumberOfTestsPair(permProbMap, lower, upper);
    		total += tests * prob;
    	}
    	int tierM = poises.get(n-1).getValue();
    	tests = expectedNumberOfTests(permProbMap, poises.get(n-1).getValue(), (a, b) -> a > b);
    	double higherThanPmProb = damages.stream().
    			filter(d -> d.getValue() > tierM).
    			mapToInt(Stance::getCount).sum() / (double) N;
    	total += tests * higherThanPmProb;
    	return total * N;
    }
    
    /**This method is meant for computing either E[T | D(e) < p1] or E[T | pm < D(e)], depending upon
    * the implementation of BiPredicate passed. Iterating over permutations:
    * 1. for E[T | D(e) < p1], the method sets as an upper bound the lowest poise tier encountered so far
    * 		in the permutation and skips all those above;
    * 2. for E[T | pm < D(e)], the method sets as a lower bound the highest poise tier encountered so far
    * 		in the permutation and skips all those below
    * The number of poises that weren't skipped is the number of tests performed given that particular permutation.
    * This number gets multiplied by the probability of the permutation's occurring.
    * The method returns the sum of these products*/
   static double expectedNumberOfTests(Map<List<Stance>, Double> permProbMap, int reference, 
		   BiPredicate<Integer, Integer> comparison) {
	   double total = 0.0;
	   for (Map.Entry<List<Stance>, Double> e : permProbMap.entrySet()) {
		   int tests = 1;
		   List<Stance> perm = e.getKey();
		   double prob = e.getValue();
		   int limitIdx = 0;
		   for (int i = 0; i < perm.size(); i++) {
			   int limit = perm.get(limitIdx).getValue();
			   int currentValue = perm.get(i).getValue();
			   if (comparison.test(currentValue, limit))
				   limitIdx = i;
			   if (comparison.test(limit, currentValue))
				   continue;
			   if (currentValue == reference)
				   break;
			   tests++;
		   }
		   total += tests * prob;
	   }
	   return total;
   }
    
   /**Computes E[T | pi < D(e) < p(i+1)] iterating through permutations, by 
    * 1. setting the ceiling at the lowest poise tier >= upperBound encountered so far in the permutation,
    * 2. setting the floor at the highest poise tier <= lowerBound encountered so far,
    * 3. skipping everything above the ceiling or below the floor.
    * The number of poises that weren't skipped is the number of tests performed given that particular permutation.
    * This number gets multiplied by the probability of that permutation's occurring.
    * The method returns the sum of these products.*/
   static double expectedNumberOfTestsPair(Map<List<Stance>, Double> permProbMap, int lowerBound, int upperBound) {
	   double total = 0.0;
	   for (Map.Entry<List<Stance>, Double> e : permProbMap.entrySet()) {
		   int tests = 1;
		   List<Stance> perm = e.getKey();
		   double prob = e.getValue();
		   int floorIdx = -1;
		   int ceilingIdx = -1;
		   boolean lowerBoundSeen = false;
		   boolean upperBoundSeen = false;
		   for (int i = 0; i < perm.size(); i++) {
			   int currentValue = perm.get(i).getValue();
			   int floor = floorIdx >= 0? perm.get(floorIdx).getValue() : currentValue;
			   int ceiling = ceilingIdx >= 0? perm.get(ceilingIdx).getValue() : currentValue;
			   if (currentValue < lowerBound) {
				   if (currentValue < floor)
					   continue;
				   floorIdx = i;
			   }
			   if (currentValue > upperBound) {
				   if (currentValue > ceiling)
					   continue;
				   ceilingIdx = i;
			   }
			   if (currentValue == lowerBound) {
				   lowerBoundSeen = true;
				   floorIdx = i;
			   }
			   if (currentValue == upperBound) {
				   upperBoundSeen = true;
				   ceilingIdx = i;
			   }
			   if (lowerBoundSeen && upperBoundSeen)
				   break;
			   tests++;
		   }
		   total += tests * prob;
	   }
	   return total;
   }
} 