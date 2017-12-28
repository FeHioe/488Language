import glob
import os
import re
import subprocess

cwd = os.path.dirname(os.path.realpath(__file__))
tests_that_should_pass = glob.glob(cwd + '/passing/*.488') + glob.glob(cwd + '/passing/**/*.488')
tests_that_should_fail = glob.glob(cwd + '/failing/*.488') + glob.glob(cwd + '/failing/**/*.488')

# These tests should be skipped in automatic testing for various reasons such as requiring user input.
SKIP_TESTS_PASSING = ['A1e.488']

# These tests do not print anything due to the specific checks they are testing.
SKIP_TESTS_OUTPUT = ['empty_scope.488']

print '\n============= ****** ==============='
print '===================================='
print 'Testing cases that should pass semantic analysis, codegen, and execution:'
count = 0
for file_path in tests_that_should_pass:
    # parent directory / directory / file
    short_file_path = '/'.join(file_path.split('/')[-3:])

    if file_path.split('/')[-1] in SKIP_TESTS_PASSING:
        continue

    # trace execution
    output = subprocess.check_output(
        ['java', '-jar', cwd + '/../dist/compiler488.jar', '-T', 'x', file_path],
        stderr=subprocess.STDOUT
    )

    if 'Semantic action' in output:
        print '==================================='
        print 'Semantically INCORRECT:', short_file_path
        count += 1
        print ''

    if 'Exception during Machine Execution' in output or 'Processing Terminated due to errors' in output:
        print '==================================='
        print 'Failed:', short_file_path,
        # Comment the next line to suppress error output.
        print '\n'.join(output.split('\n')[:-1])
        print 'Failed:', short_file_path,
        print ''
        count += 1

print '==================================='
if count == 0:
    print 'All test cases passed!'
else:
    print 'Failed {} test cases!'.format(count)

print '\n============= ****** ==============='
print '===================================='
print 'Testing cases that should fail semantic check:'
count = 0
for file_path in tests_that_should_fail:
    short_file_path = '/'.join(file_path.split('/')[-3:])
    output = subprocess.check_output(
        ['java', '-jar', cwd + '/../dist/compiler488.jar', '-X', file_path],
        stderr=subprocess.STDOUT
    )

    if 'Syntax error' not in output and 'Semantic action' not in output:
        print '==================================='
        print 'No failure detected for', short_file_path
        # Comment the next line to suppress error output.
        print '\n'.join(output.split('\n'))
        print 'No failure detected for', short_file_path
        print ''
        count += 1

print '==================================='
if count == 0:
    print 'All test cases passed!'
else:
    print 'Failed {} test cases!'.format(count)


print '\n============= ****** ==============='
print 'Verifying program write statement output:'
print '===================================='
count = 0
for test_file_path in tests_that_should_pass:
    # *.488 -> *.out
    expected_output_path = test_file_path[:-3] + 'out'

    # parent directory / directory / file
    short_test_file_path = '/'.join(test_file_path.split('/')[-3:])
    short_expected_output_path = '/'.join(expected_output_path.split('/')[-3:])

    if test_file_path.split('/')[-1] in SKIP_TESTS_PASSING + SKIP_TESTS_OUTPUT:
        continue

    if not os.path.isfile(expected_output_path):
        print "expected output file {} not found".format(short_expected_output_path)
        print "skipping output check for {}\n".format(short_test_file_path)
        continue

    # trace execution
    output = subprocess.check_output(
        ['java', '-jar', cwd + '/../dist/compiler488.jar', test_file_path],
        stderr=subprocess.STDOUT
    )

    with open(expected_output_path) as f:
        # note that we expect an extra line at the end of the output file.
        expected_output = f.read()
        # windows..
        expected_output = re.sub('\r\n', '\n', expected_output)
        # Compiler currently printing debug info even without flags..
        # this extracts the outputs corresponding to write
        # print output
        actual_output = re.search(r'Start Execution.+\n\t\n([\s\S]*)End Execution',
            output, re.M).group(1)

        if actual_output != expected_output:
            count += 1
            print "Check failed for", short_test_file_path
            print "Expected output is\n==========\n{}==========".format(expected_output)
            print "Actual output is\n==========\n{}==========".format(actual_output)

print '==================================='
if count == 0:
    print 'All test cases checked has expected output!'
else:
    print 'Output mismatch for {} test cases!'.format(count)
