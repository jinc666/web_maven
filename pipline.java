def userMap
        def proMap=['api':'test_api','report':'test_report','job':'test_job']   //定义项目字典
        def deployMap=['test-pipeline':'test-pro.sh']     //定义发版脚本
        def predeployMap=['test-pipeline':'test-pre.sh']   //定义预发布脚本
        def rollbackMap=['test-pipeline':'test-pro-rollback.sh']   //定义回滚脚本
        pipeline{
        agent any
        //parameters { string(defaultValue: '', name: 'PULL_FLAG', description: '请根据发布类型进行选择发布：\n1，输入-TESTING-发布-最新代码-到灰度\n2，输入-LATEST-发布-最新代码-到生产\n3，输入-版本号-发布-制定版本-到生产 ' ) }
        stages{
            stage('Checkout'){
                when{
                //判断是否要拉取代码
                environment name:'PULL_FLAG',value:'yes'
                }
                steps{
                    echo"${PULL_FLAG}"
                    //拉取代码
                    checkout([$class:'GitSCM',branches:[[name:'*/master']],doGenerateSubmoduleConfigurations:false,extensions:[[$class:'CleanBeforeCheckout']],submoduleCfg:[],userRemoteConfigs:[[credentialsId:'797fddfc-d9d6-42a7-9a2b-38421a6963b0',url:'git@git.test.com:test/test.git']]])
                    echo'Checkout'
                    }
                }
            stage('Build'){
                when{
                environment name:'PULL_FLAG',value:'yes'
                }
            steps{
            echo'Building'
            // 使用maven进行构建
            sh'mvn clean install -Dmaven.test.skip=true -Pprod'
            }
            }
            stage('Push package'){
            when{
            environment name:'PULL_FLAG',value:'yes'
            }
            steps{
            echo'push package'
            //上传war包至跳板机
            sh'sh /srv/shell/PAY_scp_pipe.sh '
            }
            }
            stage('Deploy/Rollback'){

            steps{
            //定义发版密码校验，只有输入口令正确，才会发版到线上机器，密码保存在环境变量中
            timeout(60){
            script{
            userMap=input message:'please input password',ok:'ok',submitter:'admin',parameters:[password(name:'password',defaultValue:'',description:'发布代码口令')],submitterParameter:'admin'

            if(userMap['password'].toString()==PASSWORD){
            echo'密码正确'
            if(DEPLOY_FLAG=='deploy'){
            echo"发版"
            sh"ssh test 'cd /data/faban && sh ${deployMap[env.JOB_NAME]} ${proMap[PROJECT]}'"
            }else if(DEPLOY_FLAG=='predeploy'){
            echo'预发布'
            sh"ssh test 'cd /data/faban/pre-release && sh ${predeployMap[env.JOB_NAME]} ${proMap[PROJECT]}'"
            }else if(DEPLOY_FLAG=='rollback'){
            echo'回滚'
            sh"ssh test 'cd /data/faban && sh ${rollbackMap[env.JOB_NAME]} ${proMap[PROJECT]}'"
            }
            }else{
            echo'密码错误'
            echo"${env.JOB_NAME}"
            }
            }
            }
            }
            }
            }
            post{
            always{
            echo'This will always run'
            //发版结束后删除jenkins workspace下的临时目录
            deleteDir()
            }
            success{
            echo'This task is successful!'
            //记录日志信息
            sh"""
            printf '%s %s %s %s %s %s' `date +'%F %H:%M:%S'` "${env.JOB_NAME}" "${proMap[PROJECT]}" "${DEPLOY_FLAG}" "success!\n" >> /srv/jk_logs/jk.log
          """
            }
            }
        }