Pod::Spec.new do |spec|
    spec.name                     = 'powersyncswift'
    spec.version                  = '0.1'
    spec.homepage                 = 'https://www.touchlab.co'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'PowerSyncSwift'
    spec.vendored_frameworks      = 'build/cocoapods/framework/powersyncswift.framework'
                
    spec.ios.deployment_target = '13.5'
                
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':powersyncswift',
        'PRODUCT_MODULE_NAME' => 'powersyncswift',
    }
                
    spec.script_phases = [
        {
            :name => 'Build PowerSync Swift',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.libraries = 'c++', 'sqlite3'
end