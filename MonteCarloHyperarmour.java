package soulsestimations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import soulsestimations.Poise.Stance;

public class MonteCarloHyperarmour {

    //CONFIG
	//50 character attacks with 0 hyperarmour, 80 character attacks with 31 hyperarmour, etc.
	private static final Stance[] HYPERARMOURS = {new Stance(0, 50), new Stance(31, 80), new Stance(61, 100), 
			new Stance(91, 60), new Stance(121, 10)};
	//150 enemy attacks with 30 stance damage, etc.
	private static final Stance[] DAMAGES = {new Stance(30, 150), new Stance(60, 200), new Stance(90, 200), 
			new Stance(120, 50)};
	//# of player attacks
    static final int M = Arrays.stream(HYPERARMOURS).mapToInt(Stance::getCount).sum();
    //# of enemy attacks
    static final int N = Arrays.stream(DAMAGES).mapToInt(Stance::getCount).sum();
    //Monte Carlo runs
    static final int TRIALS = 2000;

    static final int TOTAL_NODES = M + N;
    static final Random rnd = new Random();

    public static void main(String[] args) {
        System.out.println("Running Monte Carlo with " + M + " character attacks and " + N + 
        		" enemy attacks. " + TRIALS + " trials.");
        System.out.println();
        double[] results = new double[TRIALS];
        long t0 = System.currentTimeMillis();
        for (int t = 0; t < TRIALS; t++) {
            results[t] = runOneTrial(); 
            if ((t+1) % 100 == 0) {
                long now = System.currentTimeMillis();
                double elapsed = (now - t0)/1000.0;
                System.out.printf("Completed %d/%d trials (elapsed %.1fs)\n", t+1, TRIALS, elapsed);
            }
        }

        // statistics
        double mean = mean(results);
        double std = stddev(results, mean);
        double se = std / Math.sqrt(TRIALS);
        double ciLower = mean - 2.58 * se;
        double ciUpper = mean + 2.58 * se;
        double naive = (double) M * N;

        System.out.println("\nRESULTS");
        System.out.printf("Mean = %.3f\n", mean);
        System.out.printf("Std dev = %.3f\n", std);
        System.out.printf("99%% CI = [%.3f, %.3f]\n", ciLower, ciUpper);
        System.out.printf("Theoretical complexity (M*N) = %.0f\n", naive);
        System.out.printf("Actual vs theoretical = %.3f%%\n",
                100.0 * mean / naive);
        long t1 = System.currentTimeMillis();
        System.out.printf("Total runtime: %.1f s\n", (t1 - t0) / 1000.0);
    }
    
    /** Run a single Monte Carlo trial: sample a hidden world, run tests until stopping condition */
    static int runOneTrial() {
        //Prepare structures
        int[] nodeValues = populateNodes(HYPERARMOURS, DAMAGES);
        boolean[][] less = new boolean[TOTAL_NODES][TOTAL_NODES];
        int[] characterIndices = IntStream.range(0, M).toArray();
        int[] enemyIndices = IntStream.range(0, N).map(i -> M + i).toArray(); // enemy node indices M..M+N-1
        int testsPerformed = 0;

        //Outer loop: iterate through character attacks' values
        for (int cIdx : characterIndices) {
        	//reshuffle the order of enemy attacks for every new character attack tested
        	if (cIdx > 0)
        		shuffleArray(enemyIndices);

            //For each enemy in random order
            for (int eIdx : enemyIndices) {
                //skip tests if this pair is already resolved
                if (less[cIdx][eIdx] || less[eIdx][cIdx])
                    continue;
             
                //perform the test
                boolean tanks = nodeValues[eIdx] < nodeValues[cIdx];
                testsPerformed++;
                //Propagate the newly acquired knowledge
                if (tanks)
                    applyTransitiveClosure(less, eIdx, cIdx);
                else 
                    applyTransitiveClosure(less, cIdx, eIdx);

                if (isAttackFullyClassified(cIdx, less, M, N)) {
                    break; // move to next player attack
                }
            } // enemies loop
            if (isAllResolved(less, M, N))
                break;
        } // player loop
        return testsPerformed;
    }
    
    /**Populates 0 to M nodes with the hyperarmours of M character attacks.
     * Populated M to M+N nodes with the stance damages of N enemy attacks*/
    public static int[] populateNodes(Stance[] hyperarmours, Stance[] damages) {
    	//M hyperarmours, still ordered ascendingly
    	int[] valuesOfHA = getValues(hyperarmours, M);
    	//N stanca damages, still ordered ascendingly
    	int[] valuesOfD = getValues(damages, N);
    	//randomise their orders
    	shuffleArray(valuesOfHA);
    	shuffleArray(valuesOfD);
    	//Populate the nodes
    	return IntStream.concat(Arrays.stream(valuesOfHA), Arrays.stream(valuesOfD)).toArray();
    }
    
    /**Takes an array of hyperarmour or stance damage tiers and produces an array with as many 
     * hyperarmour or stance damage values as there are attacks of each tier; that is, an array of length M is
     * returned for hyperarmours and an array of length N for stance damages*/
    public static int[] getValues(Stance[] stances, int total) {
    	int[] values = new int[total];
    	int idx = 0;
    	for (Stance s : stances) {
    		Arrays.fill(values, idx, idx + s.getCount(), s.getValue());
    		idx += s.getCount();
    	}
    	return values;
    }

    /**Fisher-Yates algorithm for shuffling*/
    public static void shuffleArray(int[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    /** ∀j[(less(j,u) OR j = u) -> ∀k[(less(v,k) OR k = v) -> less(j,k)]]*/
    public static void applyTransitiveClosure(boolean[][] less, int u, int v) {
    	List<Integer> lesser = new ArrayList<>();
    	List<Integer> greater = new ArrayList<>();
    	lesser.add(u);
    	greater.add(v);
    	for (int j = 0; j < less.length; j++) {
    		if (less[j][u])
    			lesser.add(j);
    		if (less[v][j])
    			greater.add(j);
    	}
    	lesser.forEach(l -> greater.forEach(g -> less[l][g] = true));
    }

    /**∀i(i ∈ E -> less(cIdx, i) OR less(i, cIdx))*/
    static boolean isAttackFullyClassified(int cIdx, boolean[][] less, int M, int N) {
        int baseE = M;
        for (int i = 0; i < N; i++) {
            int eIdx = baseE + i;
            if (!less[cIdx][eIdx] && !less[eIdx][cIdx]) return false;
        }
        return true;
    }

    /** ∀i(i ∈ C -> ∀j(j ∈ E -> less(i,j) OR less(j,i))) */
    static boolean isAllResolved(boolean[][] less, int M, int N) {
        int baseE = M;
        for (int c = 0; c < M; c++) {
            for (int i = 0; i < N; i++) {
                int eIdx = baseE + i;
                if (!less[c][eIdx] && !less[eIdx][c]) 
                	return false;
            }
        }
        return true;
    }

   //simple statistics
   public static double mean(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s / arr.length;
    }

    public static double stddev(double[] arr, double mean) {
        double s = 0;
        for (double v : arr) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (arr.length - 1));
    }
}

