#!/bin/bash
set -e
if [ -z "$GIT_FILTER_BRANCH_TOOLS_PATH" ]
then
    echo set GIT_FILTER_BRANCH_TOOLS_PATH to a checkout of git@github.com:kohsuke/git-filter-branch-tools.git
    exit 2
fi
cd $(dirname $0)
SPLIT=/tmp/workflow-plugin-split
rm -rf $SPLIT
split() {
    plugin=$1
    pattern=$(perl -e '$p = $ARGV[0]; for $f (`git ls-files $p`) {chomp $f; $fs{$f} = "trunk"; for (`git log --follow -p $f`) {if (m{diff --git a/(.+) b/(.+)}) {$fs{$1} = $2 unless $fs{$1}}}} for $f (sort keys %fs) {print STDERR "$p: $f from $fs{$f}\n"} print(join "|", map {"\t$_"} sort keys %fs)' $plugin)
    git clone . $SPLIT/$plugin
    pushd $SPLIT/$plugin
    git filter-branch -f --prune-empty --msg-filter "$GIT_FILTER_BRANCH_TOOLS_PATH/record-original-commit.sh" --index-filter 'git ls-files -s | egrep "'"$pattern"'" | GIT_INDEX_FILE=$GIT_INDEX_FILE.new git update-index --index-info && ((test -f $GIT_INDEX_FILE.new && mv $GIT_INDEX_FILE.new $GIT_INDEX_FILE) || rm -f $GIT_INDEX_FILE)' HEAD
    git filter-branch -f --commit-filter "$GIT_FILTER_BRANCH_TOOLS_PATH/remove-pointless-commit.rb \"\$@\"" HEAD
    git ls-files
    mvn -f $plugin/pom.xml -DskipTests install
    popd
}
# In reactor order:
split step-api
split api
split support
split basic-steps
split durable-task-step
split scm-step
split build-step
split input-step
split stage-step
split cps
split job
split multibranch
split cps-global-lib
split aggregator
# Cleanup:
# for p in …; do mv -v $p $p-full; git clone --single-branch $p-full $p; rm -rf $p-full; (cd $p; git tag | xargs git tag -d; git gc --prune=all --aggressive; git remote set-url origin git@github.com:jenkinsci/$p.git; (echo target; echo work) >> .gitignore; git add .gitignore; perl -p -i -e 's!jenkinsci/workflow-plugin!jenkinsci/\${project.artifactId}-plugin!g' */pom.xml); done
