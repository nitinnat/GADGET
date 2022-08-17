package peersim.gossip;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import peersim.config.*;
import peersim.core.*;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SPegasos;
import weka.classifiers.functions.SPegasosGadget;
import weka.classifiers.functions.LibLINEAR;
import weka.core.Instances;
import weka.core.DistanceFunction;
import weka.core.EuclideanDistance;
import weka.core.Instance;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Option;
import weka.core.FastVector;
import weka.core.RevisionHandler;
import weka.core.RevisionUtils;
import weka.core.SelectedTag;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.core.converters.LibSVMLoader;
import weka.core.converters.SVMLightLoader;
import weka.core.neighboursearch.covertrees.Stack;
import weka.filters.Filter;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.unsupervised.attribute.NumericToNominal;

public class PegasosMLPNode implements Node
{
	// ================= fields ========================================
			// =================================================================

			/**
			 * New config option added to get the resourcepath where the resource file
			 * should be generated. Resource files are named <ID> in resourcepath.
			 * @config
			 */
			private static final String PAR_PATH = "resourcepath";
			
			private static final String PAR_SIZE = "size";


			/**
			 * New config options added to set the learning parameters of pegasos
			 * PAR_LAMBDA 	: lambda parameter, default value 0.001 
			 * PAR_MAXITER 	: maximum number of iteration, defaults to 100000
			 * PAR_EXAM_PER_ITER 	: number of examples to consider for stochastic
			 * gradient computation, defaults to 1
			 * @config
			 */
			private static final String PAR_LAMBDA = "lambda";
			private static final String PAR_MAXITER = "maxiter";
			private static final String PAR_EXAM_PER_ITER = "examperiter";
			private static final String PAR_REPLACE = "replace";
			private static final String PAR_DIM = "dim";
		        //private static final String PAR_ITER= "iter"; 

			/** used to generate unique IDs */
			private static long counterID = -1;

			/**
			 * The protocols on this node.
			 */
			protected Protocol[] protocol = null;

			/**
		 	 * learning parameters of pegasos
		 	 */
				private double lambda;
				private int max_iter;
				private int exam_per_iter;
		        private int iter;
		        private int replace;
		        private int dimension;
			
		        /**
			 * The current index of this node in the node
			 * list of the {@link Network}. It can change any time.
			 * This is necessary to allow
			 * the implementation of efficient graph algorithms.
			 */
			private int index;

			/**
			 * The fail state of the node.
			 */
			protected int failstate = Fallible.OK;

			/**
			 * The ID of the node. It should be final, however it can't be final because
			 * clone must be able to set it.
			 */
			private long ID;

			/**
			 * The prefix for the resources file. All the resources file will be in prefix 
			 * directory. later it should be taken from configuration file.
			 */
			private String resourcepath;

			/**
			 * The training dataset
			 */
			//public LabeledFeatureVector[] traindataset;

			/**
			 * The primal weight vector
			 */
			//public PrimalSVMWeights wtvector;
			
			public double weight;
			// The features at the node
			public double[] features;
			/** Misclassification count for debugging*/
			public int misclassified;
			public double obj_value;
			//Store the weights of the output
			double[] outputWtVector=null;
			//Store the weights of the hidden neurons
			double[][] hidNrnsWtMatrix = null;
			private int numNodes;
			public int numRun;
			public int converged = 0;
			public double accuracy = 0.0;
			public double trainTime = 0.0;
			public double asgTrainTime=0.0;
			public MultilayerPerceptron multClassifier = null;
			//public SPegasosGadget asgSVM = null;
			Instances trainData = null;
			Instances testData = null;
			//Instances supportVecs=null;
			//Instances selectedSetXY=null;
			//Instances updatedTrainData=null;
			File[] listOfFiles = null;
			public int numFeat;
			
			int numfiles;
			double wtnorm = 0.0;
			public long readInitTime = 0;
			// ================ constructor and initialization =================
			// =================================================================

			/** Used to construct the prototype node. This class currently does not
			 * have specific configuration parameters and so the parameter
			 * <code>prefix</code> is not used. It reads the protocol components
			 * (components that have type {@value peersim.core.Node#PAR_PROT}) from
			 * the configuration.
			 */

			
			public PegasosMLPNode(String prefix) 
			{
				System.out.println(prefix);
				String[] names = Configuration.getNames(PAR_PROT);
				resourcepath = (String)Configuration.getString(prefix + "." + PAR_PATH);
				lambda = Configuration.getDouble(prefix + "." + PAR_LAMBDA, 0.001);
				max_iter = Configuration.getInt(prefix + "." + PAR_MAXITER, 100000);
				exam_per_iter = Configuration.getInt(prefix + "." + PAR_EXAM_PER_ITER, 1);
				replace = Configuration.getInt(prefix + "." + PAR_REPLACE, 1);
				dimension = Configuration.getInt(prefix + "." + PAR_DIM, 0);
				//iter = Configuration.getInt(prefix + "." + PAR_ITER);
				System.out.println("model file and train file are saved in: " + resourcepath);
				CommonState.setNode(this);
				ID = nextID();
				protocol = new Protocol[names.length];
				for (int i=0; i < names.length; i++) 
				{
					CommonState.setPid(i);
					Protocol p = (Protocol) 
							Configuration.getInstance(names[i]);
					protocol[i] = p; 
				}
				numNodes = Configuration.getInt(prefix + "." + PAR_SIZE, 20);
				numRun = Configuration.getInt(prefix + "." + "run", 0);
				System.out.println("Number of nodes is ####### "+numNodes);
			}
			
			/**
			 * Used to create actual Node by calling clone() on a prototype node. So, actually 
			 * a Node constructor is only called once to create a prototype node and after that
			 * all nodes are created by cloning it.
			 
			 */
			
			public static void printWeights(double[] weights) {
				
				for(int i=0; i<weights.length;i++) {
					System.out.print(i + ":" + weights[i]+" ");
					
				}
				
			}
			
			// Function to get the norm of the weight vector
			public static double getNorm(double[] weights) 
			{
				 double norm = 0;
			      for (int k = 0; k < weights.length; k++)
			      {  
			          norm += (weights[k] * weights[k]);			        
			      }
				return norm;
			}
			
			public Object clone() 
			{
				PegasosMLPNode result = null;
				try
				{ 
					result=(PegasosMLPNode)super.clone(); 
				}
				catch( CloneNotSupportedException e ) 
				{
					e.printStackTrace();
				} // never happens
				result.protocol = new Protocol[protocol.length];
				CommonState.setNode(result);
				result.ID = nextID();
				for(int i=0; i<protocol.length; ++i)
				{
					CommonState.setPid(i);
					result.protocol[i] = (Protocol)protocol[i].clone();
				}
				System.out.println("creating node with ID: " + result.getID());
				// take the training datafile associated with it and call training function
				// and store the result locally in model file
				// currently training file name format is fixed and hardcoded, should be 
				// changed in future
				
				String localTrainFolderpath = resourcepath + "/" + "t_" + result.getID();
				//String trainfilename = resourcepath + "/" + "t_" + result.getID() + ".dat";
				//String modelfilename = resourcepath + "/" + "m_" + result.getID() + ".dat";
				//String testfilename = resourcepath + "/" + "tst_" + result.getID() + ".dat";
				
				// Get number of files within the train and test directories
			    File localTrainFolder = new File(localTrainFolderpath);
			    System.out.println("Train Folder Path: "+localTrainFolderpath);
			    result.listOfFiles = localTrainFolder.listFiles();
			    result.numfiles = result.listOfFiles.length;
				
				// Create a folder for this run if it does not exist
				File directory = new File(resourcepath + "/run" + result.numRun);
			    if (! directory.exists()){
			        directory.mkdir();
			        // If you require it to make the entire directory path including parents,
			        // use directory.mkdirs(); here instead.
			    }
			    
			    // Create headers to store the results
			    String csv_filename = resourcepath + "/run" + result.numRun + "/node_" + result.getID() + ".csv";
			    //String csv_filename_ASG = resourcepath + "/run" + result.numRun + "/node_asg_" + result.getID() + ".csv";
				String opString = "node,iter,obj_value,loss_value,wt_norm,obj_value_difference,converged,";
				opString += "num_converge_iters,accuracy,zero_one_error,train_time,read_init_time\n";
				
				// Write to file
				try {
				BufferedWriter bw = new BufferedWriter(new FileWriter(csv_filename));
				//BufferedWriter bwASG = new BufferedWriter(new FileWriter(csv_filename_ASG));
				bw.write(opString);
				//bwASG.write(opString);
				bw.close();
				//bwASG.close();
				}
				catch(Exception e) 
				{
				 e.printStackTrace();	
				}
				
				//Get train and test data
//				DataSource trainSource;
//				DataSource testSource;
//				Instances trainingSet = null; 
//				Instances testingSet = null;
//				Instances globalTrainingSet = null;
				//Need the train set separated into two classes
				//Instances lblOneInst =null;
				//Instances lblOtherInst=null;
				
				long startTime;
				
				try {
					//startTime = System.nanoTime(); 
					startTime=System.currentTimeMillis();
				    
					//Read in the train file
					String trainFilename = localTrainFolderpath + "/" + "t_" + result.getID() + ".arff";
					// set up the data
				    FileReader reader = new FileReader(trainFilename);
				    Instances data = new Instances (reader);
					// Make the last attribute be the class
				    int classIndex = data.numAttributes()-1; 
				    data.setClassIndex(classIndex); 
				      
				     // Get the number of features
				     numFeat = data.numAttributes()-1;
				     System.out.println("Number of features at node "+result.getID()+" "+numFeat);
				     
				     result.trainData=data;
				     //result.updatedTrainData=data;
				     
				    // Build the model
				    //SPegasosGadget cModel = new SPegasosGadget();
				    MultilayerPerceptron cMult = new MultilayerPerceptron(); 
//				    String[] optionsMLP  = new String[2];
//				    optionsMLP[0] = "-H"; 
//				    optionsMLP[1] = Double.toString(10);
//				    cMult.setOptions(optionsMLP);
				    cMult.setHiddenLayers(Double.toString(1));		    
				    cMult.setLearningRate(result.lambda);
				    cMult.setDecay(true);
				    System.out.println("Before MLP Construction");
				    //cModel.getCapabilities();
					cMult.buildClassifier(data);
					System.out.println("After MLP Construction");
					result.multClassifier = cMult;
					
					//Write the Multilayer Perceptron model
				    String locMLP = resourcepath + "/run" + result.numRun + "/binMLP_" + result.getID() + ".txt";
				    //store trained network
					//byte[] binaryNetwork = serialize(cMult);
					//writeToFile(binaryNetwork, locMLP);
				    writeMLPToFile(locMLP,cMult.toString());
					//System.out.println("Print the MLP "+cMult);
					//System.out.println("************End Printing MLP Here *********/");
					//System.out.println("Some info on hidden layers "+cMult.getHiddenLayers());
					
					//////// Figure out how to get the weights from the multilayer perceptron
					result.outputWtVector = cMult.returnOutputWts();
					//result.hidNrnsWtMatrix= cMult.returnHiddenNeuronWts();
					cMult.returnHiddenNeuronWts();
					
					//System.out.println("Length of wt. vector: " + result.wtvector.length);
					//result.wtnorm = getNorm(result.wtvector);
					//printWeights(result.wtvector);	
					//System.out.println("Norm for node " + result.getID() + ": " + result.wtnorm);
					//readInitTime = System.nanoTime() - startTime;
					readInitTime = System.currentTimeMillis()-startTime;
					System.out.println("Time taken to build the NN model "+readInitTime);
					} 
				catch (Exception e) 
					{
					// TODO Auto-generated catch block
					e.printStackTrace();
					}				
				return result;				
			}
			
			 public static byte[] serialize(Object obj) throws Exception 
			 {
			        ByteArrayOutputStream b = new ByteArrayOutputStream();
			        ObjectOutputStream o = new ObjectOutputStream(b);
			        o.writeObject(obj);
			        return b.toByteArray();
			 }
			    
			 public static Object deserialize(byte[] bytes) throws Exception 
			 {
			    	ByteArrayInputStream b = new ByteArrayInputStream(bytes);
			        ObjectInputStream o = new ObjectInputStream(b);
			        return o.readObject();
			 }
			    
			 public static void writeToFile(byte[] binaryNetwork, String dumpLocation) throws Exception 
			 {
			    FileOutputStream stream = new FileOutputStream(dumpLocation+"trained_network.txt");
			    stream.write(binaryNetwork);
			    stream.close();
			 }
			 
			 public static void writeMLPToFile(String fileName, String mlpDesc)
			 {
				 try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) 
				 {
					bw.append(mlpDesc);//Internally it does aSB.toString();
					bw.flush();
				 } 
				 catch (IOException e) 
				 {
					e.printStackTrace();
				 }
			 }
			 
			 public static MultilayerPerceptron readFromFile(String dumpLocation) 
			 {
			    	MultilayerPerceptron mlp = new MultilayerPerceptron();
			    	//binary network is saved to following file
			    	File file = new File(dumpLocation);
			    	FileInputStream fileInputStream = null;
			    	//binary content will be stored in the binaryFile variable
			    	byte[] binaryFile = new byte[(int) file.length()];
			    	try
			    	{	
			    		fileInputStream = new FileInputStream(file);
			    		fileInputStream.read(binaryFile);
			    		fileInputStream.close();			    		
			    	}
			    	catch(Exception ex)
			    	{
			    		System.out.println(ex);
			    	}
			    	try
			    	{
			        	mlp = (MultilayerPerceptron) deserialize(binaryFile);
			        }
			    	catch(Exception ex)
			    	{
			    		System.out.println(ex);
			    	}
			    	
			    	return mlp;
			    	
			    }
			 
			
		 
			/** returns the next unique ID */
			private long nextID() {

				return counterID++;
			}

			// =============== public methods ==================================
			// =================================================================


			public void setFailState(int failState) {

				// after a node is dead, all operations on it are errors by definition
				if(failstate==DEAD && failState!=DEAD) throw new IllegalStateException(
						"Cannot change fail state: node is already DEAD");
				switch(failState)
				{
				case OK:
					failstate=OK;
					break;
				case DEAD:
					//protocol = null;
					index = -1;
					failstate = DEAD;
					for(int i=0;i<protocol.length;++i)
						if(protocol[i] instanceof Cleanable)
							((Cleanable)protocol[i]).onKill();
					break;
				case DOWN:
					failstate = DOWN;
					break;
				default:
					throw new IllegalArgumentException(
							"failState="+failState);
				}
			}

			public int getFailState() { return failstate; }

			public boolean isUp() { return failstate==OK; }

			public Protocol getProtocol(int i) { return protocol[i]; }

			public int protocolSize() { return protocol.length; }

			public int getIndex() { return index; }

			public void setIndex(int index) { this.index = index; }
		        
		        public String getResourcePath(){return resourcepath;}
		        public double getPegasosLambda(){return lambda;}
		        public int getMaxIter(){return max_iter;}
		        public int getExamPerIter(){ return exam_per_iter;}
		        public int getReplace(){ return replace;}
		        public int getNumNodes(){ return numNodes;}
			/**
			 * Returns the ID of this node. The IDs are generated using a counter
			 * (i.e. they are not random).
			 */
			public long getID() { return ID; }

			public String toString() 
			{
				StringBuffer buffer = new StringBuffer();
				buffer.append("ID: "+ID+" index: "+index+"\n");
				for(int i=0; i<protocol.length; ++i)
				{
					buffer.append("protocol[" + i +"]=" + protocol[i] + "\n");
				}
				return buffer.toString();
			}

			/** Implemented as <code>(int)getID()</code>. */
			public int hashCode() { return (int)getID(); }

			/**public static void main(String[] args) {
				///Volumes/DD Recovery Storage/Research/GadgetSVM/GADGET/
				System.out.println(System.getenv("PATH"));
				PegasosNode result = new PegasosNode("/config/config-pegasosQuantum.cfg");
				
			}*/
			
			public double dotProduct(Instance inst1, double[] weights, int classIndex) 
			{
			    double result = 0;
			    int n1 = inst1.numValues();
			    int n2 = weights.length; 
			    for (int p1 = 0, p2 = 0; p1 < n1 && p2 < n2;) 
			    {
			      int ind1 = inst1.index(p1);
			      int ind2 = p2;
			      if (ind1 == ind2) 
			      {
			        if (ind1 != classIndex && !inst1.isMissingSparse(p1)) 
			        {
			          result += inst1.valueSparse(p1) * weights[p2];
			        }
			        p1++;
			        p2++;
			      } 
			      else if (ind1 > ind2) 
			      {
			        p2++;
			      } 
			      else 
			      {
			        p1++;
			      }
			    }
			    return (result);
			  }
			
			public double innerProduct(double[] vec1, double[] vec2)
			{
				double prod=0.0;
				for(int n=0;n<vec1.length;n++)
				{
					 prod=prod+vec1[n]*vec2[n];
				}
				return prod;
			}
			
			public double scalarProduct(double sc, double[] vec1)
			{
				double prod=0.0;
				for(int n=0;n<vec1.length;n++)
				{
					 prod=prod+vec1[n]*sc;
				}
				return prod;
			}


}
