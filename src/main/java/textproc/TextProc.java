package textproc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import textproc.CountComparator;
import textproc.FileUtil;

public class TextProc {

	/**
	 * @param args
	 */
	public static void main(String[] args) 	
	{				
		try {
			// Load the OpenNLP models
			Tokenizer tkizer = 
				new TokenizerME(new TokenizerModel(
						TextProc.class.getResourceAsStream(
								"/models/en-token.bin")));			
			SentenceDetector sdtor = 
				new SentenceDetectorME(new SentenceModel(
						TextProc.class.getResourceAsStream(
								"/models/en-sent.bin")));
			
			// First argument determines which operation to perform
			String opMode = args[0];
			if(opMode.equalsIgnoreCase("counts"))
			{
				// Map occurrence thresholds to vocab size (*.counts) 
				String docFilesName = args[1];	
				int maxthresh = Integer.parseInt(args[2]);
				String outputName = args[3];				
				doCounts(tkizer, docFilesName, maxthresh, outputName);				
			}
			else if(opMode.equalsIgnoreCase("makestop"))
			{
				// Augment stoplist with too-rare words (*.stop)
				String docFilesName = args[1];	
                                int thresh = Integer.parseInt(args[2]);
                                String outputName = args[3];
				
				doStop(tkizer, docFilesName, outputName, thresh);							
			}
			else if(opMode.equalsIgnoreCase("makevocab"))
			{
				// Generate vocab (*.vocab)
				String docFilesName = args[1];	
				String stopName = args[2];
				String outputName = args[3];
				doVocab(tkizer, docFilesName, outputName, stopName);
			}
			else if(opMode.equalsIgnoreCase("makecorpus"))
			{
				// Generate corpus (*.words, *.docs, *.sent)
				String docFilesName = args[1];
				String vocabName = args[2];
				String outputName = args[3];
				buildCorpus(tkizer, sdtor, docFilesName, vocabName, outputName);
			}
			else if(opMode.equals("docfilter"))
			{
				// Find documents containing *all* of our filter words
				String docFilesName = args[1];
				String filterName = args[2];
				String outputName = args[3];
				documentFilter(tkizer, docFilesName, filterName, outputName);
			}
			else 
			{
				System.out.println(
						String.format("command \"%s\" not understood", 
						opMode));			
			}
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	
	/**
	 * Given some keywords, find documents which contain ALL keywords
	 * (useful for shrinking down huge corpus)
	 * 
	 * @param docFilesName
	 * @param keywordFileName
	 * @param outname
	 * @throws IOException
	 */
	public static void documentFilter(Tokenizer tkizer, 
			String docFilesName, 
			String keywordFileName, 
			String outname) throws IOException
	{
		// Write out filenames of all documents in this directory
		// which contain *all* keywords
		//
		HashSet<String> keywords = 
			new HashSet<String>(FileUtil.readLines(keywordFileName));
		ArrayList<String> docHits = new ArrayList<String>();		
		
		// Which documents are we processing?
		//
		ArrayList<String> documents = new ArrayList<String>(); 		
		documents.addAll(FileUtil.readLines(docFilesName));
				
		int ctr = 0;
		int N = documents.size();
		for(String txtFile : documents)
		{
			System.out.println(String.format("Doc %d of %d", ctr, N));
			ctr += 1;			
			// Get set of all tokens occurring in document
			HashSet<String> docToks = new HashSet<String>(getToks(tkizer, 
					FileUtil.fileSlurp(txtFile)));										
			// Decide whether or not to add document
			if(docToks.containsAll(keywords))
				docHits.add(txtFile);				
		}	

		// Write out "hit" documents containing all keywords 
		FileWriter out = new FileWriter(new File(outname));
		for(String hit : docHits)
		{	
			out.write(String.format("%s\n", hit)); 
		}
		out.close();
	}
	
	/**
	 * Build corpus files (*.words, *.docs, *.sent) from *.doclist and *.vocab 
	 * 
	 * @param docFilesName
	 * @param vocabName
	 * @param outname
	 * @throws IOException
	 */
	public static void buildCorpus(Tokenizer tkizer, SentenceDetector sdtor, 
			String docFilesName, 
			String vocabName,
			String outname) throws IOException
	{				
		// Load vocabulary
		//
		List<String> vocabWords = FileUtil.readLines(vocabName);
		ListIterator<String> vocIter = vocabWords.listIterator();
		HashMap<String, Integer> vocab = new HashMap<String, Integer>();
		while(vocIter.hasNext())
		{			
			String word = vocIter.next();
			vocab.put(word, vocIter.previousIndex());
		}
	
		// Init output files
		//
		FileWriter wordOut = 
			new FileWriter(new File(String.format("%s.words",outname)));
		FileWriter docOut = 
			new FileWriter(new File(String.format("%s.docs",outname)));
		FileWriter sentOut = 
			new FileWriter(new File(String.format("%s.sent",outname)));
		
		// Read through actual files
		//
		List<String> documents = FileUtil.readLines(docFilesName);
		
		int i = 0;
		int di = 0;
		int si = 0;
		int D = documents.size();
		
		// FOR EACH DOCUMENT
		//
		for(String docName : documents)
		{
			System.out.println(String.format("Doc %d of %d", di, D));
			
			String doc = FileUtil.fileSlurp(docName);
			String[] sentences = sdtor.sentDetect(doc);
			
			// FOR EACH SENTENCE
			//
			for(String sent : sentences)
			{				
				// FOR EACH TOKEN
				//
				boolean emptySentence = true;
				for(String tok : getToks(tkizer, sent))
				{					
					if(vocab.containsKey(tok))
					{
						emptySentence = false;
						wordOut.write(String.format("%d ", vocab.get(tok)));
						docOut.write(String.format("%d ", di));
						sentOut.write(String.format("%d ", si));
						i += 1;
						if(i % 1000 == 0)
						{
							wordOut.write("\n");
							sentOut.write("\n");
							docOut.write("\n");
						}
					}
				}
				// Only increment the sentence counter for non-empty sentence
				//
				if(!emptySentence)
					si += 1;
			}		
			// Assumes no empty documents...!
			//
			di += 1;
		}	

		// Cleanup file handles
		wordOut.close();
		docOut.close();
		sentOut.close();
	}
	
	
	/**
	 * Construct vocabulary from documents and stopword list
	 * @param docFilesName
	 * @param outputName
	 * @param stopName
	 * @throws IOException
	 */
	public static void doVocab(Tokenizer tkizer, String docFilesName, 
			String outputName, 
			String stopName) throws IOException		
	{
		// Which documents are we processing?
		List<String> documents = FileUtil.readLines(docFilesName);

		// Get words with stopwords and allpunc filtered out 
		HashMap<String, Integer> counts = 
			puncFilter(stopFilter(getCounts(tkizer, documents), stopName)); 

		// Write out vocabulary
		Set<String> finalVocab = new HashSet<String>(counts.keySet());		
		FileUtil.writeLines(finalVocab, outputName);
	}
	
	/**
	 * Read and downcase stopwords
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static HashSet<String> readStopWords(String filename)
	{
		List<String> origStop = FileUtil.readLines(filename);
		HashSet<String> stop = new HashSet<String>();
		for(String word : origStop)
			stop.add(word.toLowerCase());
		return stop;
	}
	
	/**
	 * Filter vocabulary using *.stop stopword file (one per line)
	 * 
	 * @param counts
	 * @param stopWordFileName
	 * @return
	 * @throws IOException
	 */
	public static HashMap<String, Integer> stopFilter(
			HashMap<String, Integer> counts,
			String stopWordFileName) throws IOException 
	{
		Collection<String> stop = readStopWords(stopWordFileName);			
		for(String word : stop)		
			counts.remove(word);		
		return counts;
	}
	
	/**
	 * Output augmented stoplist for a given occurrence threshold 
	 * (useful for MALLET interop)
	 * 
	 * @param docFilesName
	 * @param outputName
	 * @param thresh
	 * @throws IOException
	 */
	public static void doStop(Tokenizer tkizer, String docFilesName, 
			String outputName, int thresh)
		throws IOException
	{
		// Which documents are we processing?
		List<String> documents = FileUtil.readLines(docFilesName);

		// Get the word counts and sort by frequency
		HashMap<String, Integer> counts = getCounts(tkizer, documents);
		String[] sortedWords = freqSortWords(counts, 1);			
		int W = sortedWords.length;
		
		// Write out words which occur less often than the threshold								
		int cutoff = 0;
						
		while(counts.get(sortedWords[cutoff]) < thresh)
				cutoff += 1;
		
		ArrayList<String> cutwords = new ArrayList<String>();
		for(int i = 0; i < cutoff; i++)
			cutwords.add(sortedWords[i]);
							
		FileUtil.writeLines(cutwords, outputName);	
	}
	
	/**
	 * Calculate vocabulary sizes for various occurrence thresholds
	 * 
	 * @param docFilesName
	 * @param outputName
	 * @throws IOException
	 */
	public static void doCounts(Tokenizer tkizer, String docFilesName, 
                                    int maxthresh,
                                    String outputName)
		throws IOException
	{
		// Which documents are we processing?
		List<String> documents = FileUtil.readLines(docFilesName);

		// Get the word counts and sort by frequency
		HashMap<String, Integer> counts = getCounts(tkizer, documents);
		String[] sortedWords = freqSortWords(counts, 1);			
		int W = sortedWords.length;
		
		// Write out the mapping from cutoffs to remaining vocabulary sizes
		FileWriter out = new FileWriter(new File(outputName));		
		int cutoff = 0;
		for(int thresh = 0;  thresh < maxthresh; thresh++)
		{					
			while(counts.get(sortedWords[cutoff]) < thresh)
				cutoff += 1;					
			out.write(String.format("%d words at thresh %d\n",
					W-cutoff, thresh));			
		}
		
		out.close();
	}
	
	/**
	 * Sort words by frequency
	 * @param counts
	 * @param sortDirection
	 * @return
	 */
	public static String[] freqSortWords(HashMap<String, Integer> counts, 
			int sortDirection)
	{
		CountComparator cc = new CountComparator(counts, sortDirection);
		Set<String> wordset = counts.keySet();
		int N = wordset.size();
		String[] sortedWords = new String[N];
		sortedWords = wordset.toArray(sortedWords);
		Arrays.sort(sortedWords, cc);
		return sortedWords;
	}
	
	/**
	 * Get token counts from a collection of files
	 * -tokenize with OpenNLP
	 * -downcase
	 * -depunc from OUTSIDE only 
	 * (eg, [I am bat-man.] becomes [I am bat-man]) 
	 * 
	 * @param dirName	
	 * @return
	 * @throws IOException
	 */	
	public static HashMap<String, Integer> getCounts(Tokenizer tkizer, 
			List<String> documents) 
	throws IOException
	{
		HashMap<String,Integer> counts = new HashMap<String,Integer>();		
		int ctr = 0;
		int N = documents.size();
		for(String txtFile : documents)
		{			
			System.out.println(String.format("Doc %d of %d", ctr + 1, N));
			ctr += 1;
						
			for(String tok : getToks(tkizer, FileUtil.fileSlurp(txtFile)))
			{                                			
				if(counts.containsKey(tok))
					counts.put(tok, counts.get(tok) + 1);
				else
					counts.put(tok, 1);
			}
		}			                				
		return counts;
	}	
	
	/**
	 * Return List of downcased, outer-de-punc'ed, tokens
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static List<String> getToks(Tokenizer tkizer, 
			String doc) throws IOException
	{
		List<String> retval = new ArrayList<String>();	
		for(String tok : tkizer.tokenize(doc))
			retval.add(outerDepunc(tok.toLowerCase()));		
		return retval;
	}
		
	/**
	 * Remove outer punctuation from a String
	 * bat-man. --> bat-man
	 * 
	 * @param s
	 * @return
	 */
	public static String outerDepunc(String s)
	{
		int L = s.length();
				
		// Remove chars off the front
		int firstChar = 0;
		while(firstChar < L &&
				!Character.isLetterOrDigit(s.charAt(firstChar)))
			firstChar++;
		
		// Remove chars off the back
		int lastChar = L-1;
		while(lastChar >= 0 && 
				!Character.isLetterOrDigit(s.charAt(lastChar)))
			lastChar--;
		
		if(firstChar >= lastChar)
			return "";
		else
			return s.substring(firstChar, lastChar + 1);
	}
	
	/**
	 * Filter out terms that consist entirely of punctuation
	 * (or that are length-zero)
	 * 
	 * @param counts
	 * @return
	 */
	public static HashMap<String, Integer> puncFilter(HashMap<String, 
			Integer> counts)
	{
		Iterator<String> keyiter = (counts.keySet()).iterator();
		while(keyiter.hasNext())			
		{
			String key = keyiter.next();
			boolean allpunc = true;
			for(int i = 0; i < key.length(); i++)
			{
				if(Character.isLetterOrDigit(key.charAt(i)))
				{
					allpunc = false;
					break;
				}				
			}
			if(allpunc)
				keyiter.remove();
		}		
		return counts;
	}
}
