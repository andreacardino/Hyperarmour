package soulsestimations;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import soulsestimations.Poise.Stance;

public class MonteCarloMixed {
    //Configuration
    static final int TRIALS = 200; //# of Monte Carlo runs
    //6 armours with 0 poise, 7 armours with 10 poise, 10 armours with 10 poise etc.
    static final Stance[] POISES = {new Stance(0, 6), new Stance(10, 7), new Stance(20, 10), new Stance(30, 13), new Stance(40, 8), 
    		new Stance(50, 4), new Stance(60, 2)};
    //15 character attacks with 20 hyperarmours, 30 character attacks with 30 hyperarmour etc.
    static final Stance[] HYPERARMOURS = {new Stance(20, 15), new Stance(30, 30), new Stance(40, 35), new Stance(50, 60), 
    		new Stance(60, 75), new Stance(70, 45), new Stance(81, 20), new Stance(90, 12), new Stance(100, 8)};
    //150 enemy attacks with 30 stance damage etc.
    static final Stance[] DAMAGES = {new Stance(30, 150), new Stance(60, 200), new Stance(90, 200), new Stance(120, 50)};
    //# of poise tiers
    static final int L = POISES.length;
    //# of character attacks
    static final int M = Arrays.stream(HYPERARMOURS).mapToInt(Stance::getCount).sum();
    //# of enemy attacks
    static final int N = Arrays.stream(DAMAGES).mapToInt(Stance::getCount).sum();
    //Derived totals
    static final int POISE_MODIFIED_ATTACK_NODES = L * M;
    static final int TOTAL_NODES = POISE_MODIFIED_ATTACK_NODES + N;

    static final Random rnd = new Random();

    public static void main(String[] args) {
        System.out.printf("Monte Carlo Poise × Hyperarmour, " + 
        		"with %d poise tiers, %d charachter attacks, %d enemy attacks. Trials: %d%n", L, M, N, TRIALS);
        System.out.println();
        double[] results = new double[TRIALS];
        long t0 = System.currentTimeMillis();
        for (int t = 0; t < TRIALS; t++) {
            results[t] = runOneTrial();
            if ((t + 1) % 10 == 0 || t == TRIALS - 1) {
                long now = System.currentTimeMillis();
                double elapsed = (now - t0) / 1000.0;
                System.out.printf("Completed %d/%d trials (elapsed %.1fs), mean: %f%n", 
                		t + 1, TRIALS, elapsed, MonteCarloHyperarmour.mean(Arrays.copyOfRange(results, 0, t)));
            }
        }

        double mean = MonteCarloHyperarmour.mean(results);
        double std = MonteCarloHyperarmour.stddev(results, mean);
        double se = std / Math.sqrt(TRIALS);
        double ciLo = mean - 2.58 * se;
        double ciHi = mean + 2.58 * se;
        double naive = (double) L * M * N;

        System.out.println("\nRESULTS");
        System.out.printf("Mean = %.3f%n", mean);
        System.out.printf("Std dev = %.3f%n", std);
        System.out.printf("99%% CI = [%.3f, %.3f]%n", ciLo, ciHi);
        System.out.printf("Naive (L*M*N) = %d%n", naive);
        System.out.printf("Reduction vs naive = %.3f%%", 
        		100.0 * mean / naive);
        long t1 = System.currentTimeMillis();
        System.out.printf("Total runtime: %.1f s%n", (t1 - t0) / 1000.0);
    }
    
    /** Run a single trial */
    static int runOneTrial() {
        //Prepare structures 
        double[] nodeValues = populateNodes(POISES, HYPERARMOURS, DAMAGES);
        boolean[][] less = new boolean[TOTAL_NODES][TOTAL_NODES];
        int[] poiseIndices = IntStream.range(0,  L).toArray();
        int[] characterIndices = IntStream.range(0, M).toArray();
        int[] enemyIndices = IntStream.range(0, N).map(idx -> L*M + idx).toArray();
        int testsPerformed = 0;
        
        //Outer loop: iterate poise tiers in biased order
        for (int pIdx : poiseIndices) {
            //Iterate character attacks in fixed characterIndices
            for (int cIdx : characterIndices) {
            	//global index for poise-modified character attack
                int playerNode = M * pIdx + cIdx;

                //reshuffle enemy attacks for every new poise-modified character attack test run
                if(cIdx > 0 || pIdx > 0) 
                	MonteCarloHyperarmour.shuffleArray(enemyIndices);

                //iterate enemy attacks
                for (int eIdx : enemyIndices) {
                    //if already deduced skip
                    if (less[playerNode][eIdx] || less[eIdx][playerNode])
                        continue;

                    //perform observation
                    boolean tanks = nodeValues[eIdx] < nodeValues[playerNode];
                    testsPerformed++;
                    
                     //TransitiveClosure(less, u, v) = ∀j((less(j,u) OR j = u) -> ∀k((less(v,k) OR k = v) -> less(j,k)))
                    if (tanks)
                        MonteCarloHyperarmour.applyTransitiveClosure(less, eIdx, playerNode);
                    else 
                        MonteCarloHyperarmour.applyTransitiveClosure(less, playerNode, eIdx);
                    
                    if (isAttackFullyClassified(playerNode, less))
                        break;
                } //enemy attacks loop
                if (isPoiseTierFullyClassified(pIdx, less))
                    break;
            } //character attacks loop
            if (isAllResolved(less))
                break;
        } //poises loop
        return testsPerformed;
    }
    
    /**Populates 0 to L*M nodes with the poise-modified hyperarmours of M character attacks.
     * Populates L*M to L*M+N nodes with the stance damages dealt by N enemy attacks*/
    static double[] populateNodes(Stance[] poises, Stance[] hyperarmours, Stance[] damages) {
    	//One of the L! possible permutations of poises, biased by how many armours grant each poise
    	int[] randomPValues = getBiasedPoiseOrder(poises);
    	//M hyperarmours, still ordered ascendingly
    	int[] haValues = MonteCarloHyperarmour.getValues(hyperarmours, M);
    	//N stance damages, still ordered ascendingly
    	int[] dValues = MonteCarloHyperarmour.getValues(damages, N);
    	//randomise the hyperarmour and stance damage orders
    	MonteCarloHyperarmour.shuffleArray(haValues);
    	MonteCarloHyperarmour.shuffleArray(dValues);
    	//Set the first L*M nodes with poise-modified hyperarmours
    	int idx = 0;
    	double[] nodes = new double[TOTAL_NODES];
    	for (int p : randomPValues) {
    		for (int h : haValues)
    			nodes[idx++] = (double) h * (1.0 + (double) p/100.0);
    	}
    	//Set the remaining nodes
    	for (int d : dValues)
    		nodes[idx++] = (double) d;
    	return nodes;
    }

    /** Weighted sampling without replacement to produce a biased permutation of poise tiers */
    static int[] getBiasedPoiseOrder(Stance[] poises) {
    	int[] order = new int[L];
    	int[] weights = Arrays.stream(poises).mapToInt(Stance::getCount).toArray();
    	int remainingTotal = Arrays.stream(weights).sum();

    	for (int i = 0; i < L; i++) {
    		int draw = (int) (rnd.nextDouble() * remainingTotal);
    		int cumulative = 0;
    		for (int j = 0; j < L; j++) {
    			cumulative += weights[j];
    			if (draw < cumulative) {
    				order[i] = poises[j].getValue();
    				remainingTotal -= weights[j];
    				weights[j] = 0;
    				break;
    			}
    		}
    	}
    	return order;
    }

    /** Check whether a poise-modified character attack is fully classified vs all enemy attacks */
    static boolean isAttackFullyClassified(int playerNode, boolean[][] less) {
        int baseE = POISE_MODIFIED_ATTACK_NODES;
        for (int e = 0; e < N; e++) {
            int eNode = baseE + e;
            if (!less[playerNode][eNode] && !less[eNode][playerNode]) 
            	return false;
        }
        return true;
    }

    /** Check whether all character attacks modified by a given poise tier are fully classified */
    static boolean isPoiseTierFullyClassified(int poiseTier, boolean[][] less) {
        int base = poiseTier * M;
        for (int a = 0; a < M; a++) {
            if (!isAttackFullyClassified(base + a, less)) 
            	return false;
        }
        return true;
    }

    /** ∀i(i ∈ PxH -> ∀j(j ∈ E -> less(i,j) OR less(j,i))) */
    static boolean isAllResolved(boolean[][] less) {
        int baseE = POISE_MODIFIED_ATTACK_NODES;
        for (int aNode = 0; aNode < baseE; aNode++) {
            for (int e = 0; e < N; e++) {
                int eNode = baseE + e;
                if (!less[aNode][eNode] && !less[eNode][aNode]) 
                	return false;
            }
        }
        return true;
    }

}
