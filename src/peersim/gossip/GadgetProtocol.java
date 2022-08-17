/*
 * Peersim-Gadget : A Gadget protocol implementation in peersim based on the paper
 * Chase Henzel, Haimonti Dutta
 * GADGET SVM: A Gossip-bAseD sub-GradiEnT SVM Solver   
 * 
 * Copyright (C) 2012
 * Deepak Nayak 
 * Columbia University, Computer Science MS'13
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package peersim.gossip;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.Arrays;
import java.lang.Integer;
import java.io.FileReader;
import java.io.LineNumberReader;

import peersim.gossip.PegasosNode;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.*;
import peersim.cdsim.*;

import java.net.MalformedURLException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.io.BufferedReader;

/**
 * Class GadgetProtocol
 * Implements a cycle based {@link CDProtocol}. It implements the Gadget algorithms
 * described in paper:
 * Chase Henzel, Haimonti Dutta
 * GADGET SVM: A Gossip-bAseD sub-GradiEnT SVM Solver   
 * 
 *  @author Nitin Nataraj, Deepak Nayak
 */


public class GadgetProtocol implements CDProtocol {
	/**
	 * New config option to get the learning parameter lambda for GADGET
	 * @config
	 */
	private static final String PAR_LAMBDA = "lambda";
	/**
	 * New config option to get the number of iteration for GADGET
	 * @config
	 */
	private static final String PAR_ITERATION = "iter";
	
	public static boolean flag = false;
	
	public static int t = 0;
	
	public static boolean optimizationDone = false;	
	
	public double EPSILON_VAL = 0.01;
	/** Linkable identifier */
	protected int lid;
	/** Learning parameter for GADGET, different from lambda parameter in pegasos */
	protected double lambda;
	/** Number of iteration (T in gadget)*/
	protected int T;
	
	private int pushsumflag = 0;
	
	public static double[][] optimalB;
	
	public static int end = 0;
	
	public static boolean pushsumobserverflag = false;
	public static final int CONVERGENCE_COUNT = 10;
	
	private double oldWeight;
	
	private boolean pushsum2_execute = true;
	
	private String protocol;
	private String protocolASG;

	private String resourcepath;
	
	private double peg_lambda;
	private int max_iter;
	private int exam_per_iter;
	private double[] weights;
	

	/**
	 * Default constructor for configurable objects.
	 */
	public GadgetProtocol(String prefix)
	{
		lambda = Configuration.getDouble(prefix + "." + PAR_LAMBDA, 0.01);
		T = Configuration.getInt(prefix + "." + PAR_ITERATION, 100);
		//T = 0;
		lid = FastConfig.getLinkable(CommonState.getPid());
		protocol = Configuration.getString(prefix + "." + "prot", "pushsum1");
		protocolASG = Configuration.getString(prefix + "." + "prot1", "pushSV");
		
	}

	/**
	 * Returns true if it possible to deliver a response to the specified node,
	 * false otherwise.
	 * Currently only checking failstate, but later may be we need to check
	 * the non-zero transition probability also
	 */
	protected boolean canDeliverRequest(Node node) {
		if (node.getFailState() == Fallible.DEAD)
			return false;
		return true;
	}
	/**
	 * Returns true if it possible to deliver a response to the specified node,
	 * false otherwise.
	 * Currently only checking failstate, but later may be we need to check
	 * the non-zero transition probability also
	 */
	protected boolean canDeliverResponse(Node node) {
		if (node.getFailState() == Fallible.DEAD)
			return false;
		return true;
	}

	/**
	 * Clone an existing instance. The clone is considered 
	 * new, so it cannot participate in the aggregation protocol.
	 */
	public Object clone() {
		GadgetProtocol gp = null;
		try { gp = (GadgetProtocol)super.clone(); }
		catch( CloneNotSupportedException e ) {} // never happens
		return gp;
	}
	
	public static double getAccuracy(Classifier cModel, String testPath) throws Exception
	{
		// Evaluate
	    // Files.listFiles() apparently does not work on Linux, and is unreliable.
	    
		File testFolder = new File(testPath);
	    File[] listOfFiles = testFolder.listFiles();
	    //int numtestfiles = listOfFiles.length;
		
		// We have to use the nio library to get the number of files in the directory
		//String[] listOfFiles = new File(testPath).list();
		
	    int numtestfiles = listOfFiles.length;
	    
	    //System.out.println("Num test files: " + numtestfiles);
        double[] spegasosPred=new double[numtestfiles];
		 double[] actual=new double[numtestfiles];
		 double acc=0; 
		 
		 DataSource testSource;
		 Instances testingSet;
		 for (int i = 0; i < numtestfiles; i++)
		 {
			 String testFilePath = listOfFiles[i].toString();
			 
				testSource = new DataSource(testFilePath);
				testingSet = testSource.getDataSet();
			 spegasosPred[i]=cModel.classifyInstance(testingSet.instance(0));
			 actual[i] = testingSet.instance(0).classValue();
			 if(spegasosPred[i]==actual[i])
			 {
				 acc=acc+1;
			 }
        //System.out.println("Acc: " + acc);
        }
		 double testAccuracy = acc/(double)numtestfiles;
		 System.out.println("Test Accuracy: " + testAccuracy);
		 return testAccuracy;
}
	
	public double getAccuracy2(Classifier cModel, Instances testingSet) throws Exception {
		// Evaluate
        double[] spegasosPred=new double[testingSet.numInstances()];
		 double[] actual=new double[testingSet.numInstances()];
		 double acc=0; 
		 int clIndex = testingSet.classIndex();
		 //System.out.println("Class index: " + clIndex);
		 //System.out.println("Num test attributes: " + testingSet.numAttributes());
		 for (int i = 0; i < testingSet.numInstances(); i++)
		 {
			 spegasosPred[i]=cModel.classifyInstance(testingSet.instance(i));
			 //System.out.println(sgdPred[i]);	
			// actual[i]=Double.parseDouble(testingSet.instance(i).getClass().toString());
			 actual[i] = testingSet.instance(i).classValue();
			 //System.out.println("Actual: "+actual[i]);
			 //System.out.println("Pred: "+spegasosPred[i]);
			 //System.out.println("=====");
			 if(spegasosPred[i]==actual[i])
			 {
				 acc=acc+1;
			 }
        
        }
		 double testAccuracy = acc/(double)testingSet.numInstances();
		// System.out.println("Accuracy "+(double)acc/testingSet.numInstances());
		 return testAccuracy;		
	}
	
	private void pushsum1(Node node, PegasosNode pn, int pid) 
	{
		PegasosNode peer = (PegasosNode)selectNeighbor(pn, pid);
	    System.out.println("Node "+node.getID()+" is gossiping with Node "+peer.getID()+"....");
	    // Function to average two weight vectors
	    System.out.println(pn.wtvector.length + " " + peer.wtvector.length);
	    
	    double[] newWeights;
	    	
    	newWeights = new double[pn.wtvector.length];
    	for(int i=0; i<pn.wtvector.length;i++) {
    		newWeights[i] = (pn.wtvector[i] + peer.wtvector[i])/2.0;
    	}
    	peer.wtvector = newWeights;
    	pn.wtvector = newWeights;
		// Save weight vectors in both pn and peer into their respective files.
		//String pn_modelfilename = pn.getResourcePath() + "/" + "m_" + pn.getID() + ".dat";
		//String peer_modelfilename = peer.getResourcePath() + "/" + "m_" + peer.getID() + ".dat";
		//System.out.println("The the two paths where the weights are being stored after pushsum are "+pn_modelfilename + 
		//	" and " + peer_modelfilename); 

		//System.out.println("Weights after pushsum: ");
		//writeWeightsToFile(pn, pn_modelfilename);
		//writeWeightsToFile(peer, peer_modelfilename);
}
		private void pushSV(Node node, PegasosNode pn, int pid) 
		{
		PegasosNode peer = (PegasosNode)selectNeighbor(pn, pid);
	    //System.out.println("ASG SVM Algorithm: Node "+node.getID()+" is gossiping with Node "+peer.getID()+"....");
	    // Function to send selected Set
	    for(int h=0;h<pn.supportVecs.numInstances();h++)
		{
			boolean notInSet=true;
			for(int f=0;f<peer.updatedTrainData.numInstances();f++)
			{	
//			  if(pn.supportVecs.instance(h).equals(peer.updatedTrainData.instance(f)))
//			  {
//				  notInSet=false;
//			  }
			  boolean instEqual=true;	
			  for(int y=0;y<pn.numFeat;y++)
			  {
				  if(pn.supportVecs.instance(h).attribute(y)!=peer.updatedTrainData.instance(f).attribute(y))
				  {
				   instEqual=false; 
				  }
			  }
			  if(instEqual==true)
			  {
				  notInSet=false;
			  }			  
			}
			//If this support vector is not in the set, then add it to the training data
			if(notInSet==true)
			{
			 peer.updatedTrainData.add(pn.supportVecs.instance(h));	
			}
		}
	    for(int h1=0;h1<peer.supportVecs.numInstances();h1++)
		{
			boolean notInSetPeer=true;
			for(int f1=0;f1<pn.updatedTrainData.numInstances();f1++)
			{	
//			  if(peer.supportVecs.instance(h1).equals(pn.updatedTrainData.instance(f1)))
//			  {
//				  notInSetPeer=false;
//			  }
				  boolean instEqualASG=true;	
				  for(int y=0;y<pn.numFeat;y++)
				  {
					  if(peer.supportVecs.instance(h1).attribute(y)!=pn.updatedTrainData.instance(f1).attribute(y))
					  {
					   instEqualASG=false; 
					  }
				  }
				  if(instEqualASG==true)
				  {
					  notInSetPeer=false;
				  }			  		  	
			}
			//If this support vector is not in the set, then add it to the training data
			if(notInSetPeer==true)
			{
			 pn.updatedTrainData.add(peer.supportVecs.instance(h1));	
			}
		}
}
	
	protected List<Node> getPeers(Node node) {
		Linkable linkable = (Linkable) node.getProtocol(lid);
		if (linkable.degree() > 0) {
			List<Node> l = new ArrayList<Node>(linkable.degree());			
			for(int i=0;i<linkable.degree();i++) {
				l.add(linkable.getNeighbor(i));
			}
			return l;
		}
		else
			return null;						
	}			

	// Comment inherited from interface
	/**
	 * This is the method where actual algorithm is implemented. This method gets    
	 * called in each cycle for each node.
	 * NOTE: The Gadget algo's iteration corresponds to the inner loop, so call this 
	 * once only, i.e. keep simulation.cycles 1
	 */
	public void nextCycle(Node node, int pid) 
	{	
		// Gets the current cycle of Gadget
		//int iter = CDState.getCycle();	
		int itrASG=CDState.getCycle();
		// Initializes the Pegasos Node
		PegasosNode pn = (PegasosNode)node;
		resourcepath = pn.getResourcePath();
		peg_lambda = pn.getPegasosLambda();
		max_iter = pn.getMaxIter();
		exam_per_iter = pn.getExamPerIter();
		
		// If converged = 0, then algorithm has not converged yet
		// Start the clock to observe training time
		//long startTime = System.nanoTime();
//		//long stASGTime = System.nanoTime();
//		if(pn.converged == 0)
//		{			
//		System.out.println("Training the model.");
//		try 
//		{		
			// Set pn.trainData here
			//Random r = new Random();
			//int cur_index = r.nextInt((pn.numfiles - 0));
			// Read the data
				/*
				 * startTime = System.nanoTime(); //System.out.println(cur_index); String
				 * curFilePath = pn.getResourcePath()+"/" + "t_" + pn.getID()+"/" + "t_" +
				 * pn.getID() + ".arff"; System.out.println("Loading " + curFilePath);
				 * FileReader curSource = new FileReader(curFilePath); Instances curDataset =
				 * new Instances(curSource); int classIndex = curDataset.numAttributes()-1;
				 * curDataset.setClassIndex(classIndex); pn.readInitTime += System.nanoTime() -
				 * startTime; pn.trainData = curDataset;
				 */
			// Randomize the examples 
			//pn.trainData.randomize(new Random(System.currentTimeMillis()));
			// Setting m_t here
			
			//startTime = System.nanoTime();
			//pn.pegasosClassifier.m_t = iter+1;
			//Train with momentum accelerated stochastic gradient
			//pn.pegasosClassifier.train(pn.trainData);
			//pn.pegasosClassifier.trainMom(pn.trainData);
			//pn.pegasosClassifier.trainNAG(pn.trainData);
			//.trainMom(pn.trainData);

			// Check if the algorithm has converged
				  
//			if(pn.pegasosClassifier.num_converge_iters == CONVERGENCE_COUNT) 
//			{
//			    	 pn.converged = 1; // Algorithm has converged on this node.
//			    	 end++;
//			 }
//			System.out.println("Obj. value: " + pn.pegasosClassifier.m_obj_value);
				
			//pushsum1(node, pn, pid);
				
			// Get norm
		/*	double norm = 0;
			for (int k = 0; k < pn.wtvector.length - 1; k++) 
			{
			   if (k != pn.trainData.classIndex()) 
			   {
			       norm += (pn.wtvector[k] * pn.wtvector[k]);
			    }
			 }*/
				
			//Project the weight and loss vectors
	/*		double scale2 = Math.min(1.0, (1.0 / (peg_lambda * norm)));
			if (scale2 < 1.0) 
			{
			   scale2 = Math.sqrt(scale2);
			   for (int j = 0; j < pn.wtvector.length - 1; j++) 
			   {
			     if (j != pn.trainData.classIndex())
			     {
			        pn.wtvector[j] *= scale2;
			      }
			    }
			 }
			System.out.println("Difference: " + pn.pegasosClassifier.m_obj_value_diff);			   			    			   
			} 
		    catch (Exception e) 
			{
		   	 e.printStackTrace();
			}*/
		
	/*	long trainTimePerIter = System.nanoTime() - startTime;
		pn.trainTime += trainTimePerIter;
		*/
		//long trainTimeASGIter = System.nanoTime() - stASGTime;
		//pn.asgTrainTime += trainTimeASGIter;
		// Get the accuracy of the test set. We don't include the accuracy calculation within
		// the training time.
		//pushsum1(node, pn, pid);
/*		if (iter % 5 == 0) 
		{
		try
		{
			String testFilePath=pn.getResourcePath() + "/" + "tst_" + pn.getID()+"/" + "tst_" +pn.getID()+".arff";
			 FileReader rTest = new FileReader(testFilePath);
			 Instances dataTest = new Instances (rTest);
			 // Make the last attribute be the class
			 int classIndex = dataTest.numAttributes()-1;
			 dataTest.setClassIndex(classIndex); 
			 pn.accuracy = getAccuracy2(pn.asgSVM,dataTest);
			 System.out.println("Accuracy from GADGET : " + pn.accuracy);
		} 
		catch (Exception e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		}*/
		
///*		if(pn.converged == 1)
//		{
//			pn.pegasosClassifier.num_converge_iters = CONVERGENCE_COUNT;
//		}
//		double trainTimeInDouble = (double)pn.trainTime/1e9;
//		double readInitTimeInDouble = (double)pn.readInitTime/1e9;
//		
//		String csv_filename_GADGET = resourcepath + "/run" + pn.numRun + "/node_" + pn.getID()  + ".csv";
//		System.out.println("Storing in " + csv_filename_GADGET);
//		String opString = pn.getID() + "," + iter + "," + pn.pegasosClassifier.m_obj_value + ","+pn.pegasosClassifier.m_loss_value;
//		opString +=  ","+pn.pegasosClassifier.wt_norm + ","+pn.pegasosClassifier.m_obj_value_diff;
//		opString += "," + pn.converged + "," + pn.pegasosClassifier.num_converge_iters + "," + pn.accuracy + ","+ (1.0 - pn.accuracy); 
//		opString += ","+ trainTimeInDouble + "," + readInitTimeInDouble + "\n"; 
		
		// Write to file
/*		try
		{
			BufferedWriter bw_GADGET = new BufferedWriter(new FileWriter(csv_filename_GADGET, true));
			bw_GADGET.write(opString);
			bw_GADGET.close();
		}
		catch(Exception e)
		{
		 e.printStackTrace();		
		}
		//pn.writeGlobalWeights("/global_");
		}*/
		
		//***********************************/
		//Do the training with the Adaptive Selective Gossip (ASG) SVM Algorithm
		//The training data should be the existing train set and the new support vectors received 
		//from another node
		//pn.updatedTrainData = pn.trainData;
		//Reset several parameters of the Pegasos node
		//including the flag for convergence, the weight vector and so on.
		pn.converged=0;
		end=0;
		long stASGTime = System.nanoTime();
		//pn.weight=0;
		try
		{
			//Train the ASG SVM Classifier
			
			pn.asgTrainTime=0.0; 
			pn.asgSVM.m_t=itrASG+1;
			//pn.wtvector=null;
			//Train the model
			pn.asgSVM.train(pn.updatedTrainData);
			pn.selectedSetXY=new Instances(pn.trainData,pn.trainData.numInstances());
			pn.supportVecs=new Instances(pn.trainData,pn.trainData.numInstances());
			//Now update the support vectors
			//Implement the Adaptive Selective Gossip (ASG) SVM Algorithm
			//Count the number of instances with label +1
			int lblOne=0; 
			int lblOther=0;
			Instances lblOneInst=new Instances(pn.trainData,pn.trainData.numInstances());
			Instances lblOtherInst=new Instances(pn.trainData,pn.trainData.numInstances());
			for(int y=0;y<pn.updatedTrainData.numInstances();y++)
			{
				if(pn.updatedTrainData.instance(y).classValue()==1)
				{
					lblOne=lblOne+1;
					lblOneInst.add(pn.updatedTrainData.instance(y));
				}
				else
				{
					lblOther=lblOther+1;
					lblOtherInst.add(pn.updatedTrainData.instance(y));
				}				
			}
			System.out.println("Number of instances with label +1 "+lblOne);
			
			Double[] Fx = new Double[lblOne];
			for(int h=0;h<lblOne; h++)
			{
				Fx[h] = 2 * pn.dotProduct(lblOneInst.instance(h),pn.wtvector,pn.numFeat+1);
			}
			Double[] Fy = new Double[lblOther];
			// Get the class label
			for(int g=0;g<lblOther;g++)
			{
				Fy[g]= 2 * pn.scalarProduct(lblOtherInst.instance(g).classValue(), pn.wtvector);
			}
			//Sort Fx and Fy and obtain 0.1% of the examples as the Selection Set.
		    int numEx=(int) Math.round(Fx.length*0.001); 
		    int numEy=(int) Math.round(Fy.length*0.001);
		    ArrayIndexComparator compFx = new ArrayIndexComparator(Fx);
		    Integer[] indexFx = compFx.createIndexArray();
		    Arrays.sort(indexFx, compFx);
		    ArrayIndexComparator compFy = new ArrayIndexComparator(Fy);
		    Integer[] indexFy = compFy.createIndexArray();
		    Arrays.sort(indexFy, compFy);
		    // Now the indexes are in appropriate order.
		    //Pick the support vectors and form the selected set
		    for(int r=0;r<numEx;r++)
		    {
		    	pn.selectedSetXY.add(lblOneInst.instance(indexFx[r]));
		    }
		    for(int r=0;r<numEy;r++)
		    {
		    	pn.selectedSetXY.add(lblOtherInst.instance(indexFy[r]));
		    }
		    pn.supportVecs=pn.selectedSetXY;
		    System.out.println("Number of support vectors "+pn.supportVecs.numInstances());
		    
			// Check if the algorithm has converged
			if(pn.asgSVM.num_converge_iters==CONVERGENCE_COUNT) 
			{
				pn.converged=1;
				end++;
			}
			//System.out.println("Obj. value: " + pn.asgSVM.m_obj_value);
			
			//Try creating the selected set
			pushSV(node, pn, pid);
			
			// Get norm
			double normASG = 0;
			for (int k = 0; k < pn.wtvector.length - 1; k++) 
			{
				if (k != pn.updatedTrainData.classIndex()) 
				{
					normASG += (pn.wtvector[k] * pn.wtvector[k]);
				}
			}
			//System.out.println("Difference: " + pn.asgSVM.m_obj_value_diff);			   			    			   
		} 
	    catch (Exception e) 
		{
	   	 e.printStackTrace();
		}
	
		long trainTimeASGIter = System.nanoTime() - stASGTime;
		pn.asgTrainTime += trainTimeASGIter;
		// Get the accuracy of the test set. We don't include the accuracy calculation within
		// the training time.
		//pushsum1(node, pn, pid);
		if (itrASG % 5 == 0) 
		{
		try
		{
			String testFilePath=pn.getResourcePath() + "/" + "tst_" + pn.getID()+"/" + "tst_" +pn.getID()+".arff";
			 FileReader rTest = new FileReader(testFilePath);
			 Instances dataTest = new Instances (rTest);
			 // Make the last attribute be the class
			 int classIndex = dataTest.numAttributes()-1;
			 dataTest.setClassIndex(classIndex); 
			 pn.accuracy = getAccuracy2(pn.asgSVM,dataTest);
			 //System.out.println("Accuracy from ASG SVM : " + pn.accuracy);
		} 
		catch (Exception e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		}
		
		if(pn.converged == 1)
		{
			pn.asgSVM.num_converge_iters = CONVERGENCE_COUNT;
		}
		double trainTimeInDouble = (double)pn.asgTrainTime/1e9;
		//We will just ignore the read time for now
		double readInitTimeInDouble = (double)pn.readInitTime/1e9;
		
		String csv_filename = resourcepath + "/run" + pn.numRun + "/node_asg_" + pn.getID()  + ".csv";
		//System.out.println("Storing in " + csv_filename);
		String opString = pn.getID() + "," + itrASG + "," + pn.asgSVM.m_obj_value + ","+pn.asgSVM.m_loss_value;
		opString +=  ","+pn.asgSVM.wt_norm + ","+pn.asgSVM.m_obj_value_diff;
		opString += "," + pn.converged + "," + pn.asgSVM.num_converge_iters + "," + pn.accuracy + ","+ (1.0 - pn.accuracy); 
		opString += ","+ trainTimeInDouble + "," + readInitTimeInDouble + "\n"; 
		
		// Write to file
		try
		{
			BufferedWriter bw = new BufferedWriter(new FileWriter(csv_filename, true));
			bw.write(opString);
			bw.close();
		}
		catch(Exception e)
		{
		 e.printStackTrace();		
		}	
		pn.writeGlobalWeights();
	}

	/**
	 * Selects a random neighbor from those stored in the {@link Linkable} protocol
	 * used by this protocol.
	 */
	protected Node selectNeighbor(Node node, int pid) {
		Linkable linkable = (Linkable) node.getProtocol(lid);
		if (linkable.degree() > 0) 
			return linkable.getNeighbor(
					CommonState.r.nextInt(linkable.degree()));
		else
			return null;
	}

	public static void writeIntoFile(String millis) {
		File file = new File("exec-time.txt");
		 
		// if file doesnt exists, then create it
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		FileWriter fw;
		try {
			fw = new FileWriter(file.getAbsoluteFile(),true);

		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(millis+"\n");
		bw.close();
		} catch (IOException e)
		 {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		

	}
	
	public void writeWtVec(String fName, double db)
	{
		BufferedWriter out = null;
		try
		{
			FileWriter fstream = new FileWriter(fName, true);
			out = new BufferedWriter(fstream);
			//IlpVector vLoss = new IlpVector(ls);
		    out.write(String.valueOf(db)); 
		    out.write("\n");
	    	}
		catch (IOException ioe) 
		{ioe.printStackTrace();}
		finally
		{
		if (out != null) 
	    {try {out.close();} catch (IOException e) {e.printStackTrace();}
	    }
		}
	}

	
// Function to write weights into the file.
	public void writeWeightsToFile(PegasosNode pn, String modelfilename)
	{
		String opString = "";
		for (int i = 0; i < pn.wtvector.length;i++) {
			if (pn.wtvector[i] != 0.0) {
				opString += i;
				opString += ":";
				opString += pn.wtvector[i];
				opString += " ";	
				
			}
			
			
		}
		
		// Write to file
		try {
		BufferedWriter bw = new BufferedWriter(new FileWriter(modelfilename));
		bw.write(opString);
		bw.close();
		}
		catch(Exception e) {
			
		}
	}
	
	

}

