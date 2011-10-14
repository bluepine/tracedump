/*
 * Copyright (C) 2011 Song Wei
 *
 * Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.gnu.org/licenses/gpl-3.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package song.androidstudy;

import org.apache.commons.cli.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import com.android.traceview.DmTraceReader;
import com.android.traceview.MethodData;
import com.android.traceview.ThreadData;
import com.android.traceview.TimeLineView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class tracedump {
	ThreadData[] mTda = null;
	TimeLineView.Record[] mTraceRecordList = null;
	ArrayList<Pattern> mIgnoreList = null;
	Set<String> mKnownList = null;

	class StackFrame {
		String className;
		String methodName;
		Long endtime;
		Long startTime;
	};

	public boolean knownMethod(MethodData d) {
		if (mKnownList == null) {
			return false;
		}
		String name = d.getClassName() + '.' + d.getMethodName();
		if (mKnownList.contains(name)) {
			// System.out.println(name + " known");
			return true;
		}
		return false;
	}

	public boolean ignoreMethod(MethodData d) {
		// boolean include = false;
		String class_name = d.getClassName();
		String method_name = d.getMethodName();
		// String name = class_name.replace('/', '.')+"."+method_name;
		String name = class_name + "." + method_name;
		// System.out.println(name);
		for (Pattern p : mIgnoreList) {
			Matcher m = p.matcher(name);
			if (m.matches()) {
				return true;
			}
		}
		return false;
	}

	boolean parseTrace(String traceFile) {
		ArrayList<TimeLineView.Record> records = null;
		DmTraceReader mReader = null;
		records = null;
		try {
			mReader = new DmTraceReader(traceFile, false);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		mTda = mReader.getThreads();
		records = mReader.getThreadTimeRecords();
		mTraceRecordList = records.toArray(new TimeLineView.Record[records
				.size()]);
		Arrays.sort(mTraceRecordList, new Comparator<TimeLineView.Record>() {
			public int compare(TimeLineView.Record rec1,
					TimeLineView.Record rec2) {
				Long start1 = new Long(rec1.block.getStartTime());
				Long start2 = new Long(rec2.block.getStartTime());
				return start1.compareTo(start2);
			}
		});

		return true;
	}

	boolean parseIgnore(String ignoreFile) {
		String line = null;
		BufferedReader in = null;
		Pattern p = null;
		try {
			in = new BufferedReader(new FileReader(ignoreFile));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		mIgnoreList = new ArrayList<Pattern>();
		do {
			try {
				line = in.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (line == null) {
				break;
			}
			try {
				p = Pattern.compile(line);
			} catch (PatternSyntaxException e) {
				e.printStackTrace();
				return false;
			}
			if (p != null) {
				mIgnoreList.add(p);
				// System.out.println(line);
			}
		} while (line != null);
		return true;
	}

	public boolean parseKnown(String knownFile) {
		mKnownList = null;
		String line = null;
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(knownFile));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		mKnownList = new HashSet<String>();
		do {
			try {
				line = in.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (line == null) {
				break;
			}
			mKnownList.add(line);
		} while (line != null);
		return true;
	}

	public boolean dump(int stackLimit) {
		if (mTraceRecordList == null) {
			return false;
		}
		HashMap<Integer, Integer> knownFrame = new HashMap<Integer, Integer>(mTda.length);
		int maxStackLevel = 0;
		HashMap<Integer, Stack<StackFrame>> threadStackMap = null;
		Stack<StackFrame> threadStack = null;
		Long endtime, startTime;
		int i;
		boolean contextSwitch;
		// TODO Auto-generated method stub
		threadStackMap = new HashMap<Integer, Stack<StackFrame>>(mTda.length);
		for (i = 0; i < mTda.length; i++) {
			threadStackMap.put(mTda[i].getId(), new Stack<StackFrame>());
			knownFrame.put(mTda[i].getId(), -1);
		}
		for (TimeLineView.Record record : mTraceRecordList) {
			TimeLineView.Block block = record.block;
			TimeLineView.Row row = record.row;
			MethodData data = block.getMethodData();
			threadStack = threadStackMap.get(row.getId());
			startTime = block.getStartTime();
			endtime = block.getEndTime();
			while (!threadStack.empty()) {
				if (threadStack.peek().endtime <= startTime) {
					// a function call returned
					if (threadStack.size() == knownFrame.get(row.getId())) {
						knownFrame.put(row.getId(), -1);
					}
					threadStack.pop();
				} else {
					if (threadStack.peek().endtime < endtime) {
						System.out.println("stack prediction error!");
					}
					break;
				}
			}
			if (!data.getClassName().equals("(context switch)")) {
				StackFrame frame = new StackFrame();
				frame.className = data.getClassName();
				frame.methodName = data.getMethodName();
				frame.startTime = startTime;
				frame.endtime = endtime;
				contextSwitch = false;
				threadStack.push(frame);
				if (knownFrame.get(row.getId()) == -1) {
					if (knownMethod(data)) {
						knownFrame.put(row.getId(), threadStack.size());
					}
				}
			} else {
				contextSwitch = true;
			}
			if (threadStack.size() > maxStackLevel) {
				maxStackLevel = threadStack.size();
			}

			if (threadStack.size() <= stackLimit) {
				String name = data.getClassName() + '.' + data.getMethodName();
				if (knownFrame.get(row.getId())== -1 || knownFrame.get(row.getId()) == threadStack.size()) {
					if (!ignoreMethod(data)) {
						for (i = 0; i < threadStack.size(); i++) {
							System.out.print("  ");
						}
						System.out.println(name + "          ::"
								+ +block.getStartTime() + "::"
								+ block.getEndTime() + "::"
								+ threadStack.size() + "::" + row.getName());
					}
				}
			}
			if (contextSwitch) {
				System.out.println("--------------------------");
			}
		}
		System.out.println("max stack level is " + maxStackLevel);
		return true;
	}

	public static void printHelp(Options options) {
		HelpFormatter f = new HelpFormatter();
		f.printHelp("java -cp <class path> song.androidstudy.tracedump ",
				options, true);
		return;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String traceFile, ignoreFile, knownFile, tmp;
		int stackLimit = 50;
		CommandLine cmd = null;
		Options options = new Options();
		options.addOption("f", "trace_file", true,
				"dalvik method profiling trace file");
		options.addOption("i", "ignore_pattern_list", true,
				"file containing patterns of classes to be ignored");
		options.addOption("k", "known_method_list", true,
				"file containing methods whose details are to be hidden");
		options.addOption("s", "stack_limit", true,
				"maximum number of stacks to be printed");
		CommandLineParser parser = new PosixParser();
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			System.out.println(e.getMessage());
			printHelp(options);
			return;
		}
		traceFile = cmd.getOptionValue("f");
		if (traceFile == null) {
			System.out.println("please specify trace file");
			printHelp(options);
			return;
		}
		ignoreFile = cmd.getOptionValue("i");
		knownFile = cmd.getOptionValue("k");
		tmp = cmd.getOptionValue("s");
		if (tmp != null) {
			stackLimit = Integer.parseInt(tmp);
		}
		tracedump d = new tracedump();
		if (ignoreFile != null) {
			if (!d.parseIgnore(ignoreFile)) {
				System.out.println("failed to parse " + ignoreFile);
				return;
			}
		}
		if (knownFile != null) {
			if (!d.parseKnown((knownFile))) {
				System.out.println("failed to parse " + knownFile);
				return;
			}
		}

		if (!d.parseTrace(traceFile)) {
			System.out.println("failed to parse " + traceFile);
			return;
		}
		d.dump(stackLimit);
	}
}
