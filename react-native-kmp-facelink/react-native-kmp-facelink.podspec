require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-kmp-facelink"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "17.0" }
  s.source       = { :git => "https://github.com/rioX432/KMP-FaceLink.git", :tag => "#{s.version}" }
  s.source_files = "ios/**/*.{h,m,mm}", "../kmp-facelink-cbridge/include/*.h", "../kmp-facelink-cbridge/src/kmpfacelink_bridge_ios.m"
  s.header_mappings_dir = "../kmp-facelink-cbridge/include"

  # KMP-FaceLink XCFramework
  s.vendored_frameworks = "ios/Frameworks/KMPFaceLink.xcframework"
  s.preserve_paths = "ios/Frameworks/KMPFaceLink.xcframework"

  s.pod_target_xcconfig = {
    "HEADER_SEARCH_PATHS" => "\"$(PODS_TARGET_SRCROOT)/../kmp-facelink-cbridge/include\"",
    "CLANG_CXX_LANGUAGE_STANDARD" => "c++17",
    "OTHER_LDFLAGS" => "-ObjC",
  }

  # React Native dependency
  install_modules_dependencies(s)
end
